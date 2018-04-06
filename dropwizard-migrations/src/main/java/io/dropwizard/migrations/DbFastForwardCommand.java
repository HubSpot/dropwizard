package io.dropwizard.migrations;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbFastForwardCommand extends AbstractLiquibaseCommand {
    protected DbFastForwardCommand() {
        super("fast-forward", "Mark the next pending change set as applied without running it");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("-n", "--dry-run")
                 .action(Arguments.storeTrue())
                 .dest("dry-run")
                 .setDefault(Boolean.FALSE)
                 .help("output the DDL to stdout, don't run it");

        subparser.addArgument("-a", "--all")
                 .action(Arguments.storeTrue())
                 .dest("all")
                 .setDefault(Boolean.FALSE)
                 .help("mark all pending change sets as applied");

        subparser.addArgument("-i", "--include")
                 .action(Arguments.store())
                 .dest("contexts")
                 .help("include change sets from the given contexts");
    }

    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void run(Namespace namespace,
                    Liquibase liquibase) throws Exception {
        final String context = getContext(namespace);
        if (namespace.getBoolean("all")) {
            if (namespace.getBoolean("dry-run")) {
                liquibase.changeLogSync(context, new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
            } else {
                liquibase.changeLogSync(context);
            }
        } else {
            if (namespace.getBoolean("dry-run")) {
                liquibase.markNextChangeSetRan(context, new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
            } else {
                liquibase.markNextChangeSetRan(context);
            }
        }
    }

    private String getContext(Namespace namespace) {
        final String contexts = namespace.get("contexts");
        return contexts == null ? "" : contexts;
    }
}
