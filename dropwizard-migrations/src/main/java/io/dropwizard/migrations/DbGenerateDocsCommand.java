package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbGenerateDocsCommand extends AbstractLiquibaseCommand {
    public DbGenerateDocsCommand() {
        super("generate-docs", "Generate documentation about the database state.");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("output").nargs(1).help("output directory");
    }

    @Override
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        liquibase.generateDocumentation(namespace.<String>getList("output").get(0));
    }
}
