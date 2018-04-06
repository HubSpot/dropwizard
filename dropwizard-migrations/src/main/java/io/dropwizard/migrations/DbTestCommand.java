package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbTestCommand extends AbstractLiquibaseCommand {
    public DbTestCommand() {
        super("test", "Apply and rollback pending change sets.");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("-i", "--include")
                 .action(Arguments.store())
                 .dest("contexts")
                 .help("include change sets from the given contexts");
    }

    @Override
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        liquibase.updateTestingRollback(getContext(namespace));
    }

    private String getContext(Namespace namespace) {
        final String contexts = namespace.get("contexts");
        return contexts == null ? "" : contexts;
    }
}
