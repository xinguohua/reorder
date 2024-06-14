package tju.edu.cn.reorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.config.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;


public class ReorderMain {

    private static final Logger LOG = LoggerFactory.getLogger(ReorderMain.class);

    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration(args);
        extendConfig(config);
        Session s = new Session(config);
        s.init();
        long startTime = System.currentTimeMillis();
        s.start();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double durationInSeconds = duration / 1000.0;

        System.out.println("duration time: " + durationInSeconds);
    }

    private static void extendConfig(Configuration config) throws IOException {
        Properties properties = new Properties();
        File cfg = new File("config.properties");
        if (!cfg.isFile()) return;
        FileInputStream fin = new FileInputStream(cfg);
        properties.load(fin);
        config.traceDir = properties.getProperty("trace_dir");
        config.outputName = "./output/";
        if (config.traceDir != null) {
            int lastSlashIndex = config.traceDir.lastIndexOf('/');
            if (lastSlashIndex == -1) {
                config.outputName += config.traceDir;
            }
            config.outputName += config.traceDir.substring(lastSlashIndex + 1);
        } else {
            LocalDateTime currentTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            config.outputName += currentTime.format(formatter);
        }
        Configuration.symbolizer = properties.getProperty("symbolizer");
        config.appname = properties.getProperty("app_name");
        config.patternType = properties.getProperty("pattern");

        LOG.info("app_name {}; trace_dir {}.", config.appname, config.traceDir);

        String val = properties.getProperty("solver_time");
        if (val != null && !val.isEmpty()) {
            config.solver_timeout = Long.parseLong(val);
            LOG.info("solver_timeout {}", config.solver_timeout);
        }
        val = properties.getProperty("solver_mem");
        if (val != null && !val.isEmpty()) {
            config.solver_memory = Long.parseLong(val);
            LOG.info("solver_mem {}", config.solver_memory);
        }
        val = properties.getProperty("window_size");
        if (val != null && !val.isEmpty()) {
            config.window_size = Long.parseLong(val);
            LOG.info("window_size {}", config.window_size);
        }

        val = properties.getProperty("only_dynamic");
        if (val != null && !val.isEmpty()) {
            config.only_dynamic = Boolean.parseBoolean(val);
            LOG.info("only_dynamic {}", config.only_dynamic);
        }

        fin.close();
    }


}
