/**
  * Small example illustrating the summaries behaviour 
  * for methods calling new.
  **/
public class CreateExample {
  private class Test {
    private int x;
    Test() {
      x = 0;
    }

    void setX(int x) {
      this.x = x;
    }

    int getX() {
      return x;
    }
  }

  private CreateExample() {
  }

  private Test createObject() {
    return new Test();
  }

  public static void main(String[] args) {
    CreateExample creator = new CreateExample();

    Test t = creator.createObject();
    assert(t.getX() == 0);
    t.setX(5);
    assert(t.getX() == 5);
    Test t2 = creator.createObject();
    assert(t2.getX() == 0);
    assert(t.getX() == 5);

  }
}