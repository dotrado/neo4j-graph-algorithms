package org.neo4j.graphalgo.algo;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.graphalgo.BetweennessCentralityProc;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.helper.graphbuilder.DefaultBuilder;
import org.neo4j.graphalgo.helper.graphbuilder.GraphBuilder;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.impl.BetweennessCentrality;
import org.neo4j.graphalgo.impl.BetweennessCentralitySuccessorBrandes;
import org.neo4j.graphalgo.impl.ParallelBetweennessCentrality;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;


/**
 * @author mknblch
 */
@RunWith(MockitoJUnitRunner.class)
public class BetweennessCentralityIntegrationTest {

    public static final String TYPE = "TYPE";

    private static GraphDatabaseAPI db;
    private static Graph graph;
    private static DefaultBuilder builder;
    private static long centerNodeId;

    @Mock
    private BetweennessCentrality.ResultConsumer consumer;

    @BeforeClass
    public static void setupGraph() throws KernelException {

        db = (GraphDatabaseAPI)
                new TestGraphDatabaseFactory()
                        .newImpermanentDatabaseBuilder()
                        .newGraphDatabase();

        builder = GraphBuilder.create(db)
                .setLabel("Node")
                .setRelationship(TYPE);

        final RelationshipType type = RelationshipType.withName(TYPE);

        /**
         * create two rings of nodes where each node of ring A
         * is connected to center while center is connected to
         * each node of ring B.
         */
        final Node center = builder.newDefaultBuilder()
                .setLabel("Node")
                .createNode();

        centerNodeId = center.getId();

        builder.newRingBuilder()
                .createRing(5)
                .forEachNodeInTx(node -> {
                    node.createRelationshipTo(center, type);
                })
                .newRingBuilder()
                .createRing(5)
                .forEachNodeInTx(node -> {
                    center.createRelationshipTo(node, type);
                });

        graph = new GraphLoader(db)
                .withAnyRelationshipType()
                .withAnyLabel()
                .withoutNodeProperties()
                .load(HeavyGraphFactory.class);

        db.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(BetweennessCentralityProc.class);
    }

    @Before
    public void setupMocks() {
        when(consumer.consume(anyLong(), anyDouble()))
                .thenReturn(true);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (db != null) db.shutdown();
        graph = null;
    }

    @Test
    public void testBCDirect() throws Exception {
        new BetweennessCentrality(graph)
                .compute()
                .forEach(consumer);
        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testSuccessorBCDirect() throws Exception {
        new BetweennessCentralitySuccessorBrandes(graph, 100_000, Pools.DEFAULT)
                .compute()
                .forEach(consumer);
        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testParallelBCDirect() throws Exception {
        new ParallelBetweennessCentrality(graph, 100_000, Pools.DEFAULT, 4)
                .compute()
                .resultStream()
                .forEach(r -> consumer.consume(r.nodeId, r.centrality));

        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testBetweennessStream() throws Exception {

        db.execute("CALL algo.betweenness.stream('Node', 'TYPE') YIELD nodeId, centrality")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    consumer.consume(
                            (long) row.getNumber("nodeId"),
                            (double) row.getNumber("centrality"));
                    return true;
                });

        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testSuccessorBetweennessStream() throws Exception {

        db.execute("CALL algo.betweenness.exp1.stream('Node', 'TYPE') YIELD nodeId, centrality")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    consumer.consume(
                            (long) row.getNumber("nodeId"),
                            (double) row.getNumber("centrality"));
                    return true;
                });

        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testParallelBetweennessStream() throws Exception {

        db.execute("CALL algo.betweenness.stream('Node', 'TYPE', {concurrency:4}) YIELD nodeId, centrality")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    consumer.consume(
                            row.getNumber("nodeId").intValue(),
                            row.getNumber("centrality").doubleValue());
                    return true;
                });

        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testSucessorBetweennessStream() throws Exception {

        db.execute("CALL algo.betweenness.exp1.stream('Node', 'TYPE') YIELD nodeId, centrality")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    consumer.consume(
                            row.getNumber("nodeId").intValue(),
                            row.getNumber("centrality").doubleValue());
                    return true;
                });

        verify(consumer, times(10)).consume(anyLong(), eq(6.0));
        verify(consumer, times(1)).consume(eq(centerNodeId), eq(25.0));
    }

    @Test
    public void testParallelBetweennessWrite() throws Exception {

        db.execute("CALL algo.betweenness('','', {concurrency:4, write:true, stats:true, writeProperty:'centrality'}) YIELD " +
                "nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    assertEquals(85.0, (double) row.getNumber("sumCentrality"), 0.01);
                    assertEquals(25.0, (double) row.getNumber("maxCentrality"), 0.01);
                    assertEquals(6.0, (double) row.getNumber("minCentrality"), 0.01);
                    assertNotEquals(-1L, row.getNumber("writeMillis"));
                    assertNotEquals(-1L, row.getNumber("computeMillis"));
                    assertNotEquals(-1L, row.getNumber("nodes"));
                    return true;
                });
    }

    @Test
    public void testParallelBetweennessWriteWithDirection() throws Exception {

        db.execute("CALL algo.betweenness('','', {direction:'<>', concurrency:4, write:true, stats:true, writeProperty:'centrality'}) YIELD " +
                "nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    assertEquals(35.0, (double) row.getNumber("sumCentrality"), 0.01);
                    assertEquals(30.0, (double) row.getNumber("maxCentrality"), 0.01);
                    assertEquals(0.5, (double) row.getNumber("minCentrality"), 0.01);
                    assertNotEquals(-1L, row.getNumber("writeMillis"));
                    assertNotEquals(-1L, row.getNumber("computeMillis"));
                    assertNotEquals(-1L, row.getNumber("nodes"));
                    return true;
                });
    }

    @Test
    public void testBetweennessWrite() throws Exception {

        db.execute("CALL algo.betweenness('','', {write:true, stats:true, writeProperty:'centrality'}) YIELD " +
                "nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    assertEquals(85.0, (double) row.getNumber("sumCentrality"), 0.01);
                    assertEquals(25.0, (double) row.getNumber("maxCentrality"), 0.01);
                    assertEquals(6.0, (double) row.getNumber("minCentrality"), 0.01);
                    assertNotEquals(-1L, row.getNumber("writeMillis"));
                    assertNotEquals(-1L, row.getNumber("computeMillis"));
                    assertNotEquals(-1L, row.getNumber("nodes"));
                    return true;
                });
    }

    @Test
    public void testBetweennessWriteWithDirection() throws Exception {

        db.execute("CALL algo.betweenness('','', {direction:'both', write:true, stats:true, writeProperty:'centrality'}) YIELD " +
                "nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    assertEquals(35.0, (double) row.getNumber("sumCentrality"), 0.01);
                    assertEquals(30.0, (double) row.getNumber("maxCentrality"), 0.01);
                    assertEquals(0.5, (double) row.getNumber("minCentrality"), 0.01);
                    assertNotEquals(-1L, row.getNumber("writeMillis"));
                    assertNotEquals(-1L, row.getNumber("computeMillis"));
                    assertNotEquals(-1L, row.getNumber("nodes"));
                    return true;
                });
    }

    @Test
    public void testSucessorBetweennessWrite() throws Exception {

        db.execute("CALL algo.betweenness.exp1('','', {write:true, stats:true, writeProperty:'centrality'}) YIELD " +
                "nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis")
                .accept((Result.ResultVisitor<Exception>) row -> {
                    assertEquals(85.0, (double) row.getNumber("sumCentrality"), 0.01);
                    assertEquals(25.0, (double) row.getNumber("maxCentrality"), 0.01);
                    assertEquals(6.0, (double) row.getNumber("minCentrality"), 0.01);
                    assertNotEquals(-1L, row.getNumber("writeMillis"));
                    assertNotEquals(-1L, row.getNumber("computeMillis"));
                    assertNotEquals(-1L, row.getNumber("nodes"));
                    return true;
                });
    }


}
