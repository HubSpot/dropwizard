package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class DbDropAllCommand extends AbstractLiquibaseCommand {
    public DbDropAllCommand() {
        super("drop-all", "Delete all user-owned objects from the database.");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("--confirm-delete-everything")
                 .action(Arguments.storeTrue())
                 .required(true)
                 .help("indicate you understand this deletes everything in your database");
    }

    @Override
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        liquibase.dropAll();
    }
}
