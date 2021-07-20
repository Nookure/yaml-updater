package ru.vyarus.yaml.updater.dropwizard;

import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import ru.vyarus.yaml.updater.YamlUpdater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Vyacheslav Rusakov
 * @since 17.07.2021
 */
public class UpdateConfigCommand extends Command {

    public UpdateConfigCommand() {
        super("update-config", "Update configuration file from the new file");
    }

    @Override
    public void configure(final Subparser subparser) {
        subparser.addArgument("file")
                .nargs(1)
                .required(true)
                .type(Arguments.fileType())
                .help("Path to updating configuration file");

        subparser.addArgument("update")
                .nargs(1)
                .required(true)
                .help("Path to new configuration file. Could also be a classpath path.");

        subparser.addArgument("-b", "--no-backup")
                .dest("backup")
                .action(Arguments.storeFalse())
                .setDefault(true)
                .help("Create backup before configuration update");

        subparser.addArgument("-d", "--delete-paths")
                .dest("delete")
                .nargs("+")
                .help("Delete properties from the current config before update");

        subparser.addArgument("-e", "--env")
                .dest("env")
                .nargs(1)
                .help("Properties file with variables for substitution in the updating file. " +
                        "Could also be a classpath path.");

        subparser.addArgument("-v", "--no-validate")
                .dest("validate")
                .action(Arguments.storeFalse())
                .setDefault(true)
                .help("Validate the resulted configuration");
    }

    @Override
    public void run(final Bootstrap<?> bootstrap, final Namespace namespace) throws Exception {
        final File current = namespace.get("file");
        final InputStream update = prepareTargetFile(namespace.get("update"));
        final boolean backup = namespace.get("backup");
        final boolean validate = namespace.get("validate");
        final List<String> delete = namespace.getList("delete");
        final Map<String, String> env = prepareEnv(namespace.get("env"));

        YamlUpdater.create(current, update)
                .backup(backup)
                .deleteProps(delete.toArray(new String[]{}))
                .validateResult(validate)
                .envVars(env)
                .update();
    }

    private InputStream prepareTargetFile(final String path) {
        final InputStream in = findFile(path);
        if (in == null) {
            throw new IllegalArgumentException("Updating file not found on path: " + path);
        }
        return in;
    }

    private Map<String, String> prepareEnv(final String envFile) {
        // always included environment vars
        final Map<String, String> res = new HashMap<>(System.getenv());

        // if provided, load variables file
        final InputStream in = findFile(envFile);
        if (in != null) {
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                final Properties props = new Properties();
                props.load(reader);

                for (Object key : props.keySet()) {
                    final String name = String.valueOf(key);
                    final String value = props.getProperty(name);
                    res.put(name, value == null ? "" : value);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to read variables from: " + envFile, ex);
            }
        }
        return res;
    }

    private InputStream findFile(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        // first check direct file
        final File file = new File(path);
        if (file.exists()) {
            try {
                return Files.newInputStream(file.toPath());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read file: " + path, e);
            }
        } else {
            // try to resolve in classpath
            return UpdateConfigCommand.class.getResourceAsStream(path);
        }
    }
}
