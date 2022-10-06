package app.cleancode.windowsTests;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Entrypoint {
public static void main(String[] args) throws Throwable {
  System.out.println("Creating the window");
  Window window = new Window("Hello windows!");
  System.out.println("Created the window");
  window.show();
  System.out.println("Showed the window");
  Thread.sleep(1000);
  window.setBackgroundColor(new Color(255, 0, 255, 255));
  Thread.sleep(1000);
}
}
