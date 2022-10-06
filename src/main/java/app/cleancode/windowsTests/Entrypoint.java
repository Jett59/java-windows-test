package app.cleancode.windowsTests;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Entrypoint {
public static void main(String[] args) throws Throwable {
  Window window = new Window("Hello windows!");
  window.show();
  Thread.sleep(1000);
  window.setBackgroundColor(new Color(255, 0, 255, 255));
  window.setPos(0, 0);
  window.setSize(1920, 1080);
  window.setTitle("Changed the title!");
  Thread.sleep(1000);
  System.exit(0);
}
}
