package app.cleancode.windowsTests;

import java.util.function.Function;
import app.cleancode.windowsTests.platform.PlatformWindow;
import app.cleancode.windowsTests.platform.windows.WindowsWindow;

public class Window {
  private static Function<String, PlatformWindow> platformWindowConstructor;
  static {
    try {
    String os = System.getProperty("os.name");
    if (os.startsWith("Windows")) {
      platformWindowConstructor = WindowsWindow::new;
    } else {
      throw new RuntimeException("Unsupported platform %s".formatted(os));
    }
    }catch (Exception e) {
      throw new RuntimeException("Failed to bind to implementation of PlatformWindow", e);
    }
  }

  private PlatformWindow platformWindow;

  public Window(String title) {
    try {
      platformWindow = (PlatformWindow) platformWindowConstructor.apply(title);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
  
  public void setTitle(String title) {
    platformWindow.setTitle(title);
  }
  public void setBackgroundColor(Color color) {
    platformWindow.setBackground(color);
  }
  public void setPos(int x, int y) {
    platformWindow.setPos(x, y);
  }
  public void setSize(int width, int height) {
    platformWindow.setSize(width, height);
  }
  public void show() {
    platformWindow.show();
  }
}
