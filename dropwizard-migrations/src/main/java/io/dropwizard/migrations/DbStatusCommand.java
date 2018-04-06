package io.dropwizard.migrations;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbStatusCommand extends AbstractLiquibaseCommand {

    private PrintStream outputStream = System.out;

    @VisibleForTesting
    void setOutputStream(PrintStream outputStream) {
        this.outputStream = outputStream;
    }

    public DbStatusCommand() {
        super("status", "Check for pending change sets.");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("-v", "--verbose")
                 .action(Arguments.storeTrue())
                 .dest("verbose")
                 .help("Output verbose information");
        subparser.addArgument("-i", "--include")
                 .action(Arguments.store())
                 .dest("contexts")
                 .help("include change sets from the given contexts");
    }

    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        liquibase.reportStatus(MoreObjects.firstNonNull(namespace.getBoolean("verbose"), false),
                               getContext(namespace),
                               new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    private String getContext(Namespace namespace) {
        final String contexts = namespace.get("contexts");
        return contexts == null ? "" : contexts;
    }
}
