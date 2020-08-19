package com.example.flutter_serial_port;

import java.lang.Thread;
import java.lang.Runnable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;
import android.widget.Switch;

import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** FlutterSerialPortPlugin */
public class FlutterSerialPortPlugin implements MethodCallHandler, EventChannel.StreamHandler {

  private static final String TAG = "FlutterSerialPortPlugin";
  private SerialPortFinder mSerialPortFinder = new SerialPortFinder();
  protected SerialPort mSerialPort;
  protected OutputStream mOutputStream;
  private InputStream mInputStream;
  private ReadThread mReadThread;
  private EventChannel.EventSink mEventSink;
  private Handler mHandler = new Handler(Looper.getMainLooper());;

  private class ReadThread extends Thread {
    @Override
    public void run() {
      super.run();
      while (!isInterrupted()) {
        int size;
        try {
          byte[] buffer = new byte[64];
          if (mInputStream == null)
            return;
          size = mInputStream.read(buffer);
          Log.d(TAG, "read size: " + String.valueOf(size));
          if (size > 0) {
            onDataReceived(buffer, size);
          }
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
      }
    }
  }

  protected void onDataReceived(final byte[] buffer, final int size) {
    if (mEventSink != null) {
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          Log.d(TAG, "eventsink: " + buffer.toString());
          mEventSink.success( Arrays.copyOfRange(buffer, 0, size));
        }
      });
    }
  }

  FlutterSerialPortPlugin(Registrar registrar) {
    final EventChannel eventChannel = new EventChannel(registrar.messenger(), "serial_port/event");
    eventChannel.setStreamHandler(this);
  }

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "serial_port");
    channel.setMethodCallHandler(new FlutterSerialPortPlugin(registrar));
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    Log.d(TAG, "call.method " + call.method);
    switch (call.method) {
    case "getPlatformVersion":
      result.success("Android " + android.os.Build.VERSION.RELEASE);
      break;
    case "open":
      final String devicePath = call.argument("devicePath");
      final int baudrate = call.argument("baudrate");
      Log.d(TAG, "Open " + devicePath + ", baudrate: " + baudrate);
      Boolean openResult = openDevice(devicePath, baudrate);
      result.success(openResult);
      break;
    case "close":
      Boolean closeResult = closeDevice();
      result.success(closeResult);
      break;
    case "tcnCommand":
      try {
        Log.d(TAG, "call.arguments() " + call.arguments() );

        JSONObject obj = new JSONObject((String) call.arguments());
        Log.d(TAG, "obj " + obj );
        writeData(obj);
        Log.d(TAG, "obj " + obj );

        result.success(true);
      } catch (JSONException e) {
        e.printStackTrace();
      }
      break;
    case "getAllDevices":
      ArrayList<String> devices = getAllDevices();
      Log.d(TAG, devices.toString());
      result.success(devices);
      break;
    case "getAllDevicesPath":
      ArrayList<String> devicesPath = getAllDevicesPath();
      Log.d(TAG, devicesPath.toString());
      result.success(devicesPath);
      break;
    default:
      result.notImplemented();
      break;
    }
  }

  @Override
  public void onListen(Object o, EventChannel.EventSink eventSink) {
    mEventSink = eventSink;
  }

  @Override
  public void onCancel(Object o) {
    mEventSink = null;
  }

  private ArrayList<String> getAllDevices() {
    ArrayList<String> devices = new ArrayList<String>(Arrays.asList(mSerialPortFinder.getAllDevices()));
    return devices;
  }

  private ArrayList<String> getAllDevicesPath() {
    ArrayList<String> devicesPath = new ArrayList<String>(Arrays.asList(mSerialPortFinder.getAllDevicesPath()));
    return devicesPath;
  }

  private Boolean openDevice(String devicePath, int baudrate) {
    if (mSerialPort == null) {
      /* Check parameters */
      if ((devicePath.length() == 0) || (baudrate == -1)) {
        return false;
      }

      /* Open the serial port */
      try {
        mSerialPort = new SerialPort(new File(devicePath), baudrate, 0);
        mOutputStream = mSerialPort.getOutputStream();
        mInputStream = mSerialPort.getInputStream();
        mReadThread = new ReadThread();
        mReadThread.start();
        return true;
      } catch (Exception e) {
        Log.e(TAG, e.toString());
        return false;
      }
    }
    return false;
  }

  private Boolean closeDevice() {
    if (mSerialPort != null) {
      mSerialPort.close();
      mSerialPort = null;
      return true;
    }
    return false;
  }

  private void getStatusElevator() {
    byte[] bytesToSend = {0x02,0x03,0x01,0x00,0x00,0x03,0x03};
    try {
      mOutputStream.write(bytesToSend, 0, 7);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void writeData(JSONObject obj) {
    try {
      byte[] bytesToSend;
      String command = (String) obj.get("command");
      switch (command) {

        case "setDouble":
          int slot = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00,0xFF,0xCA,0x00, (byte)(slot),0x00,0x00,(byte) (byte)(slot),0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 6);
        break;
        case "statusElevator":
          getStatusElevator();
        break;
        case "shipment":
          // int slot = Integer.parseInt((String) obj.get("data"));
          // int adjust = slot + 0 + 0 + 0;

          // bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte) slot,0x00,0x00,(byte) adjust,0x03,0x05};
          // mOutputStream.write(bytesToSend, 0, 10);

          // getStatusElevator();

          int slot = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte)(slot),0x00,0x00,(byte) (byte)(slot),0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 10);
          
          break;
        case "d":
          String[] range = ((String) obj.get("data")).split("-");
          //int adjust2 = 5 + 0 + 0 + 0;

          for (int i = (Integer.parseInt(range[0])); i <= (Integer.parseInt(range[1])); i++) {
            bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte)(Integer.parseInt(range[2])),0x00,0x00,(byte) (byte)(Integer.parseInt(range[2])),0x03, (byte)i};
            mOutputStream.write(bytesToSend, 0, 10);
            Thread.sleep(200);
          }

          break;

        case "d2":
          int slot3 = Integer.parseInt((String) obj.get("data"));
          int adjust3 = slot3 + 0 + 0 + 0;

          bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte) slot3,0x00,0x00,(byte) adjust3,0x03, (byte)slot3};
          mOutputStream.write(bytesToSend, 0, 10);
          Thread.sleep(200);


          break;


        case "cf"://clearElevatorFault
          /*bytesToSend = new byte[]{0x02,0x03,(byte) Integer.parseInt((String) obj.get("data")),0x00, 0x00, 0x03, 0x03};
          mOutputStream.write(bytesToSend, 0, 7);*/

          /*bytesToSend = new byte[]{0x02,0x03, 0x50,0x00, 0x00, 0x03, 0x03};
          mOutputStream.write(bytesToSend, 0, 7);*/

          // for (int i = 0; i < 100; i++) {
          //   bytesToSend = new byte[]{0x02,0x03, 0x50,0x00, 0x00, 0x03, (byte)i};
          //   mOutputStream.write(bytesToSend, 0, 7);
          //   Thread.sleep(200);
          // }

          bytesToSend = new byte[]{0x02, 0x03, 0x50, 0x00, 0x00, 0x03, (byte)82};
          mOutputStream.write(bytesToSend, 0, 7);
          Thread.sleep(200);

          break;
        case "to"://backElevatorToOrigin
          bytesToSend = new byte[]{0x02, 0x03, 0x05,0x00, 0x00, 0x03, 0x05};
              mOutputStream.write(bytesToSend, 0, 7);
            Thread.sleep(200);

          // for (int i = 0; i < 100; i++) {
          //   bytesToSend = new byte[]{0x02,0x03, 0x05,0x00, 0x00, 0x03, (byte)i};
          //   mOutputStream.write(bytesToSend, 0, 7);
          //   Thread.sleep(200);
          // }
          break;
      }

      /*
      *
A faixa de cálculo BCC inclui: STX + comprimento do pacote de comunicação + comando + pacote de dados + ETX
* 2 + X + 2 + 3 = 7
      * */

      /*byte[] bytesToSend0 = {0x00, (byte) 0xFF, (byte) 0x83, (byte) 0xAC, 0x55, (byte) 0xAA};
      mOutputStream.write(bytesToSend0, 0, 10);
      Log.e(TAG, "Write data" + Arrays.toString(bytesToSend0));
      Thread.sleep(1000);

      byte[] bytesToSend = {0x02,0x06,0x02,0x00,0x05,0x00,0x00,0x01,0x03,0x05};
      mOutputStream.write(bytesToSend, 0, 10);
      Log.e(TAG, "Write data" + Arrays.toString(bytesToSend));*/
/*
      byte[] bytesToSend = {0x02,0x03,0x01,0x00,0x00,0x03,0x03};
      Log.e(TAG, "Write data 1" + Arrays.toString(bytesToSend));
      mOutputStream.write(bytesToSend, 0, 7);
      Thread.sleep(1000);


      byte[] bytesToSend2 = {0x02,0x06,0x02 ,0x00, 0x05 ,0x00 ,0x00 ,0x01, 0x03, 0x05};
      Log.e(TAG, "Write data 2" + Arrays.toString(bytesToSend2));
      mOutputStream.write(bytesToSend2, 0, 10);
      Thread.sleep(1000);
*/

    } catch (IOException e) {
      Log.e(TAG, e.toString());
    } catch (JSONException e) {
      Log.e(TAG, e.toString());
    } catch (InterruptedException e) {
      Log.e(TAG, e.toString());
    }
  }
}
