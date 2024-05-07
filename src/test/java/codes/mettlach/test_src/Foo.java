package codes.mettlach.test_src;

public class Foo extends Bar {
    private String name;
    public Integer epoc;

    public Foo(String name) {
      this.name = name;
      this.epoc = 1234;
    }

    public Foo() {
      this("N/A");
      this.epoc = 1234;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getName() {
        return "foo";
    }

    public Foo me() {
        return this;
    }

    public Integer getArray(Integer p1) {
        return p1;
    }

    public int test() {
        veryPrivate();
        return 42;
    }

    private void veryPrivate() {
        System.out.println("private");
    }

    protected void fizz() {

    }
}
