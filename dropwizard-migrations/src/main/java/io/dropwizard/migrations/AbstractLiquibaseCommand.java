package io.dropwizard.migrations;

import liquibase.Liquibase;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public abstract class AbstractLiquibaseCommand {
    private final String name;
    private final String description;

    protected AbstractLiquibaseCommand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    protected abstract void run(Namespace namespace, Liquibase liquibase) throws Exception;

    public void configure(Subparser subparser) {}

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
