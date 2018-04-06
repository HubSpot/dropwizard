package io.dropwizard.migrations;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbMigrateCommand extends AbstractLiquibaseCommand {

    private PrintStream outputStream = System.out;

    @VisibleForTesting
    void setOutputStream(PrintStream outputStream) {
        this.outputStream = outputStream;
    }

    public DbMigrateCommand() {
        super("migrate", "Apply all pending change sets.");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("-n", "--dry-run")
                 .action(Arguments.storeTrue())
                 .dest("dry-run")
                 .setDefault(Boolean.FALSE)
                 .help("output the DDL to stdout, don't run it");

        subparser.addArgument("-c", "--count")
                 .type(Integer.class)
                 .dest("count")
                 .help("only apply the next N change sets");

        subparser.addArgument("-i", "--include")
                 .action(Arguments.store())
                 .dest("contexts")
                 .setDefault("job,service")
                 .help("include change sets from the given contexts");
    }

    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        final String context = getContext(namespace);
        validateContext(context, liquibase);
        final Integer count = namespace.getInt("count");
        final boolean dryRun = MoreObjects.firstNonNull(namespace.getBoolean("dry-run"), false);
        if (count != null) {
            if (dryRun) {
                liquibase.update(count, context, new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            } else {
                liquibase.update(count, context);
            }
        } else {
            if (dryRun) {
                liquibase.update(context, new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            } else {
                liquibase.update(context);
            }
        }
    }

    private String getContext(Namespace namespace) {
        final String contexts = namespace.get("contexts");
        return contexts == null ? "" : contexts;
    }

    private static void validateContext(String context, Liquibase liquibase) {
        Set<String> contextParts = Splitter
            .on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(context)
            .stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        if (contextParts.isEmpty() && !databaseIsLocal(liquibase)) {
            throw new IllegalStateException("Running context-less migration in qa or prod ist verboten - that would cause all contexts to run which is only allowed in local environment.");
        } else if (contextParts.contains("legacy")) {
            throw new IllegalStateException("Running 'legacy' context migration ist verboten.\n" +
                "      These contexts have special meaning and their changesets are never meant to be run in a production environment, nor even locally in isolation.");
        } else if (contextParts.contains("manual")) {
            throw new IllegalStateException("Running 'manual' context migration ist verboten.\n" +
                "      These contexts have special meaning and their changesets are never meant to be run in a production environment, nor even locally in isolation.");
        } else if (contextParts.contains("blocking")) {
            throw new IllegalStateException("Running 'blocking' context migration ist verboten.\n" +
                "      This context has special meaning solely as a marker and its changesets are never meant to be run in isolation.");
        } else if (contextParts.contains("non-blocking")) {
            throw new IllegalStateException("Running 'non-blocking' context migration ist verboten.\n" +
                "      This context has special meaning solely as a marker and its changesets are never meant to be run in isolation.");
        }
    }

    private static boolean databaseIsLocal(Liquibase liquibase) {
        String databaseUrl = liquibase.getDatabase().getConnection().getURL();
        return databaseUrl.contains("localhost") || databaseUrl.contains("127.0.0.1");
    }
}
