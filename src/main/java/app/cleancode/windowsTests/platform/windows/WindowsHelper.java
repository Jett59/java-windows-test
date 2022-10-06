package app.cleancode.windowsTests.platform.windows;

import java.lang.foreign.MemoryAddress;
import static java.lang.foreign.MemoryAddress.NULL;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.ValueLayout;
import app.cleancode.bindings.win32.WINDOWS;
import app.cleancode.windowsTests.Color;

public class WindowsHelper {
  public static String getLastErrorMessage() {
    try (MemorySession memorySession = MemorySession.openConfined()) {
      MemorySegment messageAddressPointer = memorySession.allocate(ValueLayout.ADDRESS);
      int errorCode = WINDOWS.GetLastError();
      long messageSize = WINDOWS.FormatMessageA(
          WINDOWS.FORMAT_MESSAGE_ALLOCATE_BUFFER() | WINDOWS.FORMAT_MESSAGE_FROM_SYSTEM()
              | WINDOWS.FORMAT_MESSAGE_IGNORE_INSERTS(),
          NULL, errorCode, 0, messageAddressPointer.address(), 0, NULL);
      MemoryAddress messageAddress = messageAddressPointer.get(ValueLayout.ADDRESS, 0);
      if (messageAddress == NULL) {
        throw new RuntimeException("Failed to fetch the error message");
      }
      MemorySegment messageSegment =
          MemorySegment.ofAddress(messageAddress, messageSize, memorySession);
      String message = new String(messageSegment.toArray(ValueLayout.JAVA_BYTE));
      return "%s (%d)".formatted(message.trim(), errorCode);
    }
  }
  
  public static int getColorCode(Color color) {
    // ABGR (transparency = 255 - opacity, blue, green, red)
    return ((255 - color.opacity()) << 24) | (color.blue() << 16) | (color.green() << 8) | (color.red() << 0);
  }
}
