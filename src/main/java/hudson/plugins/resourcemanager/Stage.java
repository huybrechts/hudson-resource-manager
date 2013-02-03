package hudson.plugins.resourcemanager;

enum Stage {

    setUp("Set up"), tearDown("Tear down");

    private final String name;

    Stage(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
