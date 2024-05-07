package codes.mettlach.test_src;

import codes.mettlach.Presenter;

@Presenter(value=Foo.class, instanceName="foo")
public class FooPresenter {
    private Foo foo;

    public FooPresenter(Foo foo) {
        this.foo = foo;
    }

    public String getDisplayName() {
        return "My name is " + foo.getName();
    }

    public String getAdminDisplayName() {
        return "ADMIN: " + foo.getName();
    }

    public String testarooni(int p1) {
        return foo.getName();
    }
}
