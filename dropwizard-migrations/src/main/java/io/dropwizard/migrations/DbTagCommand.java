package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbTagCommand extends AbstractLiquibaseCommand {
    public DbTagCommand() {
        super("tag", "Tag the database schema.");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("tag-name")
                .dest("tag-name")
                .nargs(1)
                .required(true)
                .help("The tag name");
    }

    @Override
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        liquibase.tag(namespace.<String>getList("tag-name").get(0));
    }
}
