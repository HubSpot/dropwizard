package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.inf.Namespace;

public class DbClearChecksumsCommand extends AbstractLiquibaseCommand {
    public DbClearChecksumsCommand() {
        super("clear-checksums", "Removes all saved checksums from the database log");
    }

    @Override
    public void run(Namespace namespace,
                    Liquibase liquibase) throws Exception {
        liquibase.clearCheckSums();
    }
}
