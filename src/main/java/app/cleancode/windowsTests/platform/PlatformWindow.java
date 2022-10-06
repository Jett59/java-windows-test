package app.cleancode.windowsTests.platform;

import app.cleancode.windowsTests.Color;

public interface PlatformWindow {
  void show();

  void setBackground(Color color);

  void setPos(int x, int y);

  void setSize(int width, int height);

  void setTitle(String title);
}
