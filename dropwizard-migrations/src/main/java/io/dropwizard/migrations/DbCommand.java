package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.util.SortedMap;
import java.util.TreeMap;

public class DbCommand extends AbstractLiquibaseCommand {
    private static final String COMMAND_NAME_ATTR = "subcommand";
    private final SortedMap<String, AbstractLiquibaseCommand> subcommands;

    public DbCommand() {
        super("db", "Run database migration tasks");
        this.subcommands = new TreeMap<>();
        addSubcommand(new DbCalculateChecksumCommand());
        addSubcommand(new DbClearChecksumsCommand());
        addSubcommand(new DbDropAllCommand());
        addSubcommand(new DbDumpCommand());
        addSubcommand(new DbFastForwardCommand());
        addSubcommand(new DbGenerateDocsCommand());
        addSubcommand(new DbLocksCommand());
        addSubcommand(new DbMigrateCommand());
        addSubcommand(new DbPrepareRollbackCommand());
        addSubcommand(new DbRollbackCommand());
        addSubcommand(new DbStatusCommand());
        addSubcommand(new DbTagCommand());
        addSubcommand(new DbTestCommand());
    }

    private void addSubcommand(AbstractLiquibaseCommand subcommand) {
        subcommands.put(subcommand.getName(), subcommand);
    }

    public void configure(ArgumentParser parser) {
        for (AbstractLiquibaseCommand subcommand : subcommands.values()) {
            final Subparser cmdParser = parser.addSubparsers()
                                              .addParser(subcommand.getName())
                                              .setDefault(COMMAND_NAME_ATTR, subcommand.getName())
                                              .description(subcommand.getDescription());
            subcommand.configure(cmdParser);
        }
    }

    @Override
    public void run(Namespace namespace, Liquibase liquibase) throws Exception {
        final AbstractLiquibaseCommand subcommand = subcommands.get(namespace.getString(COMMAND_NAME_ATTR));
        subcommand.run(namespace, liquibase);
    }
}
