package hudson.plugins.global_build_stats.business;

import hudson.model.Hudson;
import hudson.plugins.global_build_stats.GlobalBuildStatsPlugin;
import hudson.plugins.global_build_stats.model.*;
import hudson.plugins.global_build_stats.xstream.GlobalBuildStatsXStreamConverter;
import hudson.security.Permission;
import hudson.util.DaemonThreadFactory;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author fcamblor
 * Class is intended to provide a unique access point to the GlobalBuildStatsPlugin modifier
 * for save() action
 */
public class GlobalBuildStatsPluginSaver {

    private static final Logger LOGGER = Logger.getLogger(GlobalBuildStatsPluginSaver.class.getName());

    private GlobalBuildStatsPlugin plugin;

    /**
     * See {@link #updatePlugin(hudson.plugins.global_build_stats.business.GlobalBuildStatsPluginSaver.BeforeSavePluginCallback)}
     * Use of a size 1 thread pool frees us from worring about accidental thread death.
     */
    /*package*/ final ExecutorService writer = Executors.newFixedThreadPool(1, new DaemonThreadFactory());

    public static abstract class BeforeSavePluginCallback {

        public abstract void changePluginStateBeforeSavingIt(GlobalBuildStatsPlugin plugin);

        public void afterPluginSaved(){
        }
    }

    public GlobalBuildStatsPluginSaver(GlobalBuildStatsPlugin plugin){
        this.plugin = plugin;

        // Initializing xtream bindings
        this.initializeXStream();
    }

    private void initializeXStream() {
        Hudson.XSTREAM.registerConverter(new GlobalBuildStatsXStreamConverter());

		// XStream compacting aliases...
		Hudson.XSTREAM.alias(GlobalBuildStatsXStreamConverter.JOB_BUILD_RESULT_CLASS_ALIAS, JobBuildResult.class);
		Hudson.XSTREAM.alias(GlobalBuildStatsXStreamConverter.BUILD_STAT_CONFIG_CLASS_ALIAS, BuildStatConfiguration.class);
		Hudson.XSTREAM.alias(GlobalBuildStatsXStreamConverter.BUILD_SEARCH_CRITERIA_CLASS_ALIAS, BuildSearchCriteria.class);
		Hudson.XSTREAM.alias(GlobalBuildStatsXStreamConverter.HISTORIC_SCALE_CLASS_ALIAS, HistoricScale.class);
		Hudson.XSTREAM.alias(GlobalBuildStatsXStreamConverter.YAXIS_CHART_TYPE_CLASS_ALIAS, YAxisChartType.class);
		Hudson.XSTREAM.alias(GlobalBuildStatsXStreamConverter.YAXIS_CHART_DIMENSION_CLASS_ALIAS, YAxisChartDimension.class);

		Hudson.XSTREAM.aliasField("t", BuildStatConfiguration.class, "buildStatTitle");
		Hudson.XSTREAM.aliasField("w", BuildStatConfiguration.class, "buildStatWidth");
		Hudson.XSTREAM.aliasField("h", BuildStatConfiguration.class, "buildStatHeight");
		Hudson.XSTREAM.aliasField("l", BuildStatConfiguration.class, "historicLength");
		Hudson.XSTREAM.aliasField("s", BuildStatConfiguration.class, "historicScale");
		Hudson.XSTREAM.aliasField("yact", BuildStatConfiguration.class, "yAxisChartType");
		Hudson.XSTREAM.aliasField("ds", BuildStatConfiguration.class, "dimensionsShown");
		Hudson.XSTREAM.aliasField("f", BuildStatConfiguration.class, "buildFilters");
		// Deprecated ! Just here for old formats
		Hudson.XSTREAM.aliasField("jf", BuildStatConfiguration.class, "jobFilter");
		Hudson.XSTREAM.aliasField("sbr", BuildStatConfiguration.class, "shownBuildResults");

		Hudson.XSTREAM.aliasField("jf", BuildSearchCriteria.class, "jobFilter");
		Hudson.XSTREAM.aliasField("nf", BuildSearchCriteria.class, "nodeFilter");
		Hudson.XSTREAM.aliasField("lf", BuildSearchCriteria.class, "launcherFilter");
		Hudson.XSTREAM.aliasField("sbr", BuildSearchCriteria.class, "shownBuildResults");

		Hudson.XSTREAM.aliasField("r", JobBuildResult.class, "result");
		Hudson.XSTREAM.aliasField("n", JobBuildResult.class, "jobName");
		Hudson.XSTREAM.aliasField("nb", JobBuildResult.class, "buildNumber");
		Hudson.XSTREAM.aliasField("d", JobBuildResult.class, "buildDate");
		Hudson.XSTREAM.aliasField("du", JobBuildResult.class, "duration");
		Hudson.XSTREAM.aliasField("nn", JobBuildResult.class, "nodeName");
		Hudson.XSTREAM.aliasField("un", JobBuildResult.class, "userName");
    }

    public void reloadPlugin() {
        try {
            this.plugin.load();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Single entry point to persist information on GlobalBuildStatsPlugin
     * As the number of builds grow, the time it takes to execute "plugin.save()" become
     * non-trivial, up to the order of minutes or more. So to prevent this from blocking executor threads
     * that execute this callback, we use {@linkplain #writer a separate thread} to asynchronously persist
     * them to the disk.
     * @param callback
     */
    public void updatePlugin(BeforeSavePluginCallback callback){
        final List<BuildStatConfiguration> configsBeforeStateChange = new ArrayList<BuildStatConfiguration>(plugin.getBuildStatConfigs());
        callback.changePluginStateBeforeSavingIt(plugin);
        LOGGER.log(Level.FINER, "Global build stats state update queued !");

        writer.submit(new Runnable(){
            public void run(){

                // this happens if other runnables have written bits in a bulk
                if (configsBeforeStateChange.equals(plugin.getBuildStatConfigs())
                        && !plugin.getJobBuildResultsSharder().pendingChanges()){
                    LOGGER.log(Level.FINER, "No change detected in update queue !");
                    return;
                }

                // Persist everything
                try {
                    plugin.save();
                    LOGGER.log(Level.FINER, "Changes applied and file saved !");
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to persist global build stat records", e);
                }
            }
        });
    }
}
