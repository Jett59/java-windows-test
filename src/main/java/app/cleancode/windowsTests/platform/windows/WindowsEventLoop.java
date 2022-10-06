package app.cleancode.windowsTests.platform.windows;

import static java.lang.foreign.MemoryAddress.*;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import app.cleancode.bindings.win32.CREATESTRUCT;
import app.cleancode.bindings.win32.MSG;
import app.cleancode.bindings.win32.WINDOWS;
import app.cleancode.bindings.win32.WNDCLASS;
import app.cleancode.bindings.win32.WNDPROC;

public class WindowsEventLoop {
  private static MemoryAddress instanceHandle;
  private static MemoryAddress defaultWindowProc;
  private static MemoryAddress messageWindow;
  private static CountDownLatch messageWindowLatch;
  private static int EVENT_THREAD_EXECUTE_MESSAGE;

  static {
    System.loadLibrary("kernel32");
    System.loadLibrary("user32");
    System.loadLibrary("gdi32");
    instanceHandle = WINDOWS.GetModuleHandleA(NULL);
    defaultWindowProc = WNDPROC.allocate((windowHandle, message, wParam, lParam) -> {
      MemoryAddress instanceWindowProc;
      if (message == WINDOWS.WM_NCCREATE()) {
        MemorySegment createParams = MemorySegment.ofAddress(MemoryAddress.ofLong(lParam),
            CREATESTRUCT.sizeof(), MemorySession.openImplicit());
        instanceWindowProc = CREATESTRUCT.lpCreateParams$get(createParams);
        WINDOWS.SetWindowLongPtrA(windowHandle, WINDOWS.GWLP_USERDATA(),
            instanceWindowProc.toRawLongValue());
      } else {
        instanceWindowProc =
            MemoryAddress.ofLong(WINDOWS.GetWindowLongPtrA(windowHandle, WINDOWS.GWLP_USERDATA()));
      }
      if (instanceWindowProc != NULL) {
        return WNDPROC.ofAddress(instanceWindowProc, MemorySession.openImplicit())
            .apply(windowHandle, message, wParam, lParam);
      } else {
        return WINDOWS.DefWindowProcA(windowHandle, message, wParam, lParam);
      }
    }, MemorySession.global()).address();
    EVENT_THREAD_EXECUTE_MESSAGE =
        WINDOWS.RegisterWindowMessageA(SegmentAllocator.newNativeArena(MemorySession.global())
            .allocateUtf8String("22abc178-8e9e-4cce-ac01-6bc10d034827"));
    messageWindowLatch = new CountDownLatch(1);
    Thread.ofPlatform().name("Windows Event Processing Thread").daemon().start(() -> {
      MemoryAddress messageWindowClassName = SegmentAllocator.newNativeArena(MemorySession.global())
          .allocateUtf8String("Message Window Class").address();
      MemorySegment messageWindowClass = WNDCLASS.allocate(MemorySession.global());
      WNDCLASS.lpszClassName$set(messageWindowClass, messageWindowClassName);
      WNDCLASS.hInstance$set(messageWindowClass, instanceHandle);
      WNDCLASS.lpfnWndProc$set(messageWindowClass,
          WNDPROC.allocate((windowHandle, message, wParam, lParam) -> {
            if (message == EVENT_THREAD_EXECUTE_MESSAGE) {
              try {
                return (long) Linker.nativeLinker().downcallHandle(MemoryAddress.ofLong(lParam),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG)).invoke();
              } catch (Throwable e) {
                throw new RuntimeException("Unexpected exception thrown by window thread callback",
                    e);
              }
            } else {
              return WINDOWS.DefWindowProcA(windowHandle, message, wParam, lParam);
            }
          }, MemorySession.global()).address());
      if (WINDOWS.RegisterClassA(messageWindowClass) == 0) {
        throw new RuntimeException("Failed to create the message window class: %s"
            .formatted(WindowsHelper.getLastErrorMessage()));
      }
      messageWindow = WINDOWS.CreateWindowExA(
          0, messageWindowClassName, SegmentAllocator.newNativeArena(MemorySession.global())
              .allocateUtf8String("Message Window"),
          0, 0, 0, 0, 0, WINDOWS.HWND_MESSAGE(), NULL, instanceHandle, NULL);
      if (messageWindow == NULL) {
        throw new RuntimeException("Failed to create the message window: %s"
            .formatted(WindowsHelper.getLastErrorMessage()));
      }
      MemorySegment message = MSG.allocate(MemorySession.global());
      while (true) {
        if (messageWindowLatch.getCount() > 0) {
          messageWindowLatch.countDown();
        }
        WINDOWS.GetMessageA(message, NULL, 0, 0);
        WINDOWS.TranslateMessage(message);
        WINDOWS.DispatchMessageA(message);
      }
    });
  }

  public static MemoryAddress getInstanceHandle() {
    return instanceHandle;
  }

  public static MemoryAddress getDefaultWindowProc() {
    return defaultWindowProc;
  }

  public static interface Callback {
    long run();
  }

  public static void runOnEventThread(Callback callback) {
    WINDOWS.EnableWindow(defaultWindowProc, EVENT_THREAD_EXECUTE_MESSAGE);
    try (MemorySession memorySession = MemorySession.openConfined()) {
      messageWindowLatch.await();
      Thread.sleep(100);
      WINDOWS.SendMessageA(messageWindow, EVENT_THREAD_EXECUTE_MESSAGE, 0, Linker.nativeLinker()
          .upcallStub(MethodHandles.lookup()
              .findVirtual(Callback.class, "run", MethodType.methodType(long.class))
              .bindTo(callback), FunctionDescriptor.of(ValueLayout.JAVA_LONG), memorySession)
          .address().toRawLongValue());
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke callback on event thread", e);
    }
  }
}
