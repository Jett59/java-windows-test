package app.cleancode.windowsTests.platform.windows;

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.util.concurrent.CountDownLatch;
import static java.lang.foreign.MemoryAddress.NULL;
import app.cleancode.bindings.win32.PAINTSTRUCT;
import app.cleancode.bindings.win32.WINDOWS;
import app.cleancode.bindings.win32.WNDCLASS;
import app.cleancode.bindings.win32.WNDPROC;
import app.cleancode.windowsTests.Color;
import app.cleancode.windowsTests.platform.PlatformWindow;

public class WindowsWindow implements PlatformWindow {
  private static MemoryAddress instanceHandle;
  private static MemoryAddress className;
  static {
    instanceHandle = WindowsEventLoop.getInstanceHandle();
    className = SegmentAllocator.implicitAllocator().allocateUtf8String("Window").address();
    MemorySegment windowClass = WNDCLASS.allocate(MemorySession.global());
    WNDCLASS.lpfnWndProc$set(windowClass, WindowsEventLoop.getDefaultWindowProc());
    WNDCLASS.lpszClassName$set(windowClass, className);
    WNDCLASS.hInstance$set(windowClass, instanceHandle);
    WINDOWS.RegisterClassA(windowClass);
  }

  private static final Cleaner cleaner = Cleaner.create();

  private MemoryAddress windowHandle;
  private MemorySession instanceMemorySession;
  private SegmentAllocator instanceSegmentAllocator;

  private Color backgroundColor = new Color(255, 255, 255, 255);
  private int x, y, width, height;

  public WindowsWindow(String title) {
    CountDownLatch createdWindowLatch = new CountDownLatch(1);
    WindowsEventLoop.runOnEventThread(() -> {
      instanceMemorySession = MemorySession.openConfined(cleaner);
      instanceSegmentAllocator = SegmentAllocator.newNativeArena(instanceMemorySession);
      MemorySegment titlePointer = instanceSegmentAllocator.allocateUtf8String(title);
      MemorySegment windowProcPointer =
          WNDPROC.allocate(this::windowsEventLoop, instanceMemorySession);
      windowHandle =
          WINDOWS.CreateWindowExA(0, className, titlePointer, WINDOWS.WS_OVERLAPPEDWINDOW(),
              WINDOWS.CW_USEDEFAULT(), WINDOWS.CW_USEDEFAULT(), WINDOWS.CW_USEDEFAULT(),
              WINDOWS.CW_USEDEFAULT(), NULL, NULL, instanceHandle, windowProcPointer);
      createdWindowLatch.countDown();
      return 0l;
    });
    try {
      createdWindowLatch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException("Failed to wait for the window to be created", e);
    }
  }

  public long windowsEventLoop(MemoryAddress windowHandle, int message, long wParam, long lParam) {
    if (message == WINDOWS.WM_SIZE()) {
      this.width = (int) (lParam & 0xffff);
      this.height = (int) ((lParam >> 16) & 0xffff);
      return 0;
    } else if (message == WINDOWS.WM_MOVE()) {
      this.x = (int) (lParam & 0xffff);
      this.y = (int) ((lParam >> 16) & 0xffff);
      return 0;
    } else if (message == WINDOWS.WM_PAINT()) {
      try (MemorySession memorySession = MemorySession.openConfined()) {
        SegmentAllocator segmentAllocator = SegmentAllocator.newNativeArena(memorySession);
        MemorySegment paintStruct = PAINTSTRUCT.allocate(segmentAllocator);
        MemoryAddress hdc = WINDOWS.BeginPaint(windowHandle, paintStruct);
        MemoryAddress backgroundBrush =
            WINDOWS.CreateSolidBrush(WindowsHelper.getColorCode(backgroundColor));
        WINDOWS.FillRect(hdc, PAINTSTRUCT.rcPaint$slice(paintStruct), backgroundBrush);
        WINDOWS.DeleteObject(backgroundBrush);
        WINDOWS.EndPage(hdc);
      }
      return 0;
    } else if (message == WINDOWS.WM_CLOSE()) {
      WINDOWS.DestroyWindow(windowHandle);
      WINDOWS.PostQuitMessage(0);
      return 0;
    }
    return WINDOWS.DefWindowProcA(windowHandle, message, wParam, lParam);
  }

  @Override
  public void show() {
    // We have to do this on the event thread otherwise the window doesn't get focus properly.
    WindowsEventLoop.runOnEventThread(() -> {
      return WINDOWS.ShowWindow(windowHandle, WINDOWS.SW_NORMAL());
    });
  }

  @Override
  public void setBackground(Color color) {
    this.backgroundColor = color;
    WINDOWS.RedrawWindow(windowHandle, NULL, NULL, WINDOWS.RDW_INVALIDATE());
  }

  @Override
  public void setPos(int x, int y) {
    WINDOWS.MoveWindow(windowHandle, x, y, width, height, WINDOWS.TRUE());
  }

  @Override
  public void setSize(int width, int height) {
    WINDOWS.MoveWindow(windowHandle, x, y, width, height, WINDOWS.TRUE());
  }

  @Override
  public void setTitle(String title) {
    WindowsEventLoop.runOnEventThread(() -> {
      return WINDOWS.SetWindowTextA(windowHandle,
          instanceSegmentAllocator.allocateUtf8String(title));
    });
  }
}
