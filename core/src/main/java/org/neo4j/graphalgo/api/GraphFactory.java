package org.neo4j.graphalgo.api;

import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.HugeNullWeightMap;
import org.neo4j.graphalgo.core.HugeWeightMap;
import org.neo4j.graphalgo.core.IdMap;
import org.neo4j.graphalgo.core.NodeImporter;
import org.neo4j.graphalgo.core.NullWeightMap;
import org.neo4j.graphalgo.core.WeightMap;
import org.neo4j.graphalgo.core.huge.HugeIdMap;
import org.neo4j.graphalgo.core.huge.HugeNodeImporter;
import org.neo4j.graphalgo.core.utils.ImportProgress;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressLoggerAdapter;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.logging.NullLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The Abstract Factory defines the construction of the graph
 *
 * @author mknblch
 */
public abstract class GraphFactory {

    public static final String TASK_LOADING = "LOADING";

    protected final ExecutorService threadPool;
    protected final GraphDatabaseAPI api;
    protected final GraphSetup setup;
    protected final GraphDimensions dimensions;
    protected final ImportProgress progress;

    protected Log log = NullLog.getInstance();
    protected ProgressLogger progressLogger = ProgressLogger.NULL_LOGGER;

    public GraphFactory(GraphDatabaseAPI api, GraphSetup setup) {
        this.threadPool = setup.executor;
        this.api = api;
        this.setup = setup;
        this.log = setup.log;
        this.progressLogger = progressLogger(log, setup.logMillis, TimeUnit.MILLISECONDS);
        dimensions = new GraphDimensions(api, setup).call();
        progress = new ImportProgress(
                progressLogger,
                setup.tracker,
                dimensions.hugeNodeCount(),
                dimensions.maxRelCount(),
                setup.loadIncoming,
                setup.loadOutgoing);
    }

    public abstract Graph build();

    protected IdMap loadIdMap() throws EntityNotFoundException {
        final NodeImporter nodeImporter = new NodeImporter(
                api,
                progress,
                dimensions.nodeCount(),
                dimensions.labelId());
        return nodeImporter.call();
    }

    protected HugeIdMap loadHugeIdMap(AllocationTracker tracker) throws EntityNotFoundException {
        final HugeNodeImporter nodeImporter = new HugeNodeImporter(
                api,
                tracker,
                progress,
                dimensions.hugeNodeCount(),
                dimensions.allNodesCount(),
                dimensions.labelId());
        return nodeImporter.call();
    }

    protected WeightMapping newWeightMap(int propertyId, double defaultValue) {
        return propertyId == StatementConstants.NO_SUCH_PROPERTY_KEY
                ? new NullWeightMap(defaultValue)
                : new WeightMap(dimensions.nodeCount(), defaultValue, propertyId);
    }

    protected HugeWeightMapping hugeWeightMapping(
            AllocationTracker tracker,
            int propertyId,
            double defaultValue) {
        return propertyId == StatementConstants.NO_SUCH_PROPERTY_KEY
                    ? new HugeNullWeightMap(defaultValue)
                    : new HugeWeightMap(dimensions.hugeNodeCount(), defaultValue, tracker);
    }

    private static ProgressLogger progressLogger(Log log, long time, TimeUnit unit) {
        if (log == NullLog.getInstance()) {
            return ProgressLogger.NULL_LOGGER;
        }
        ProgressLoggerAdapter logger = new ProgressLoggerAdapter(log, TASK_LOADING);
        if (time > 0) {
            logger.withLogIntervalMillis((int) Math.min(unit.toMillis(time), Integer.MAX_VALUE));
        }
        return logger;
    }
}
