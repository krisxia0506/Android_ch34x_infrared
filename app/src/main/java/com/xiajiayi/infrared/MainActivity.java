package com.xiajiayi.infrared;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import cn.wch.uartlib.WCHUARTManager;
import cn.wch.uartlib.chipImpl.type.ChipType2;
import cn.wch.uartlib.exception.ChipException;
import cn.wch.uartlib.exception.NoPermissionException;
import cn.wch.uartlib.exception.UartLibException;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "cn.wch.wchusbdriver.USB_PERMISSION";
    private Handler handler;
    private EditText Et_Send;
    private Button clearButton;
    private Button sendButton;
    private static int baudRate=115200;       //波特率
    private static byte dataBit=8;           //数据位
    private static byte stopBit=1;           //停止位
    private static byte parity=0;            //校验
    private static boolean flowControl=false;       //流控
    private Context context;

    private UsbDevice usbDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.context=this;
        setContentView(R.layout.activity_main); // 设置Activity的布局
        if(!UsbFeatureSupported()){
            showToast("系统不支持USB Host功能");
            System.exit(0);
            return;
        }

        clearButton = findViewById(R.id.clearButton); // 通过ID找到清除按钮
        sendButton = findViewById(R.id.sendButton); // 通过ID找到发送按钮
        String one = "680800FF12001116";
        String two = "680800FF12011216";
        byte[] oneBytes = hexStringToBytes(one);
        byte[] twoBytes = hexStringToBytes(two);

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("USB", "按下了第一组按钮，发送数据: ");
                int i = writeData(usbDevice, 0, oneBytes, oneBytes.length);
                Log.i("USB", "成功发送数据长度: "+i);
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("USB", "按下了第二组按钮，发送数据: ");
                int i = writeData(usbDevice, 0, twoBytes, twoBytes.length);
                Log.i("USB", "成功发送数据长度: "+i);
            }
        });
        //动态申请权限
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},111);
        }
        WCHUARTManager.getInstance().init(this.getApplication());
        Log.i("USB", "开始enumDevice方法: ");
        //枚举设备
        enumDevice();
        Log.i("USB", "枚举结束: ");
        //延迟1秒
        handler=new Handler();

        Log.i("USB", "开始设置串口参数: ");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //设置串口参数
                setSerialParameter(usbDevice,0);
            }
        },3000);

    }


    /**
     * 系统是否支持USB Host功能
     *
     * @return true:系统支持USB Host false:系统不支持USB Host
     */
    public boolean UsbFeatureSupported() {
        boolean bool = this.getPackageManager().hasSystemFeature(
                "android.hardware.usb.host");
        return bool;
    }
    private void showToast(String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ToastUtil.create(context,message).show();
                Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
            }
        });
    }

    void enumDevice(){

        try {
            Log.i("USB", "enumDevice()开始枚举设备: ");
            showToast("开始搜索设备");
            //枚举符合要求的设备
            ArrayList<UsbDevice> usbDeviceArrayList = WCHUARTManager.getInstance().enumDevice();
            Log.i("USB", "设备列表:"+ usbDeviceArrayList);
            if(usbDeviceArrayList.size()==0){
                Log.i("USB", "没有找到设备");
                showToast("没有找到设备");
                return;
            }
            Log.i("USB", "找到设备数量: "+usbDeviceArrayList.size());
            showToast("找到: "+usbDeviceArrayList.size()+"个设备");
            usbDevice = usbDeviceArrayList.get(0);
            //连接第一个设备
            open(usbDevice);
            showToast("设备连接成功");
            ChipType2 chipType = WCHUARTManager.getInstance().getChipType(usbDevice);
            showToast("芯片类型: "+chipType);
            Log.i("USB", "芯片类型: "+chipType);
            Log.i("USB", "串口号："+usbDevice.getSerialNumber());
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
    }
    //打开连接设备
    void open(@NonNull UsbDevice usbDevice){
        if(WCHUARTManager.getInstance().isConnected(usbDevice)){
            showToast("当前设备已经打开");
            return;
        }
        try {
            //打开设备
            boolean b = WCHUARTManager.getInstance().openDevice(usbDevice);
            if(b){
                Log.i("USB", "打开成功: "+usbDevice);
                showToast("打开成功");
                //打开成功
                //更新显示的ui
//                update(usbDevice);
                //初始化接收计数
                int serialCount = 0;
                try {
                    serialCount = WCHUARTManager.getInstance().getSerialCount(usbDevice);
                } catch (Exception e) {
                    e.printStackTrace();
                }
//                for (int i = 0; i < serialCount; i++) {
//                    readCountMap.put(FormatUtil.getSerialKey(usbDevice,i),0);
//                }
//                //将该设备添加至已打开设备列表,在读线程ReadThread中,将会读取该设备的每个串口数据
//                addToReadDeviceSet(usbDevice);
//                //用作文件对比测试,在打开每个设备时，对每个串口新建对应的保存数据的文件
//                if(FILE_TEST){
//                    for (int i = 0; i < serialCount; i++) {
//                        linkSerialToFile(usbDevice,i);
//                    }
//                }
//                registerModemStatusCallback(usbDevice);
//                registerDataCallback(usbDevice);
            }else {
                showToast("打开失败");
            }
        } catch (ChipException e) {
            LogUtil.d(e.getMessage());
        } catch (NoPermissionException e) {
            //没有权限打开该设备
            //申请权限
            showToast("没有权限打开该设备");
            requestPermission(usbDevice);
        } catch (UartLibException e) {
            e.printStackTrace();
        }
    }

    /**
     * 申请读写权限
     * @param usbDevice
     */
    private void requestPermission(@NonNull UsbDevice usbDevice){
        try {
            WCHUARTManager.getInstance().requestPermission(this,usbDevice);
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
    }

    //设置串口参数
    boolean setSerialParameter(UsbDevice usbDevice,int serialNumber){
        try {
            boolean b = WCHUARTManager.getInstance().setSerialParameter(usbDevice, serialNumber, baudRate, dataBit, stopBit, parity, flowControl);
            showToast("设置串口参数是否成功: "+b);
            Log.i("USB", "设置串口参数是否成功: "+b);
            return b;
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
        return false;
    }

    //写数据
    int writeData(UsbDevice usbDevice,int serialNumber,@NonNull byte[] data,int length){
        try {
            int write = WCHUARTManager.getInstance().writeData(usbDevice, serialNumber, data, length,2000);
            showToast("发送数据长度: "+write);
            return write;
        } catch (Exception e) {
            LogUtil.d(e.getMessage());

        }
        return -2;
    }

    /**
     * 将Hex String转换为Byte数组
     *
     * @param hexString the hex string
     * @return the byte [ ]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString==null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toLowerCase();
        final byte[] byteArray = new byte[hexString.length() >> 1];
        int index = 0;
        for (int i = 0; i < hexString.length(); i++) {
            if (index  > hexString.length() - 1) {
                return byteArray;
            }
            byte highDit = (byte) (Character.digit(hexString.charAt(index), 16) & 0xFF);
            byte lowDit = (byte) (Character.digit(hexString.charAt(index + 1), 16) & 0xFF);
            byteArray[i] = (byte) (highDit << 4 | lowDit);
            index += 2;
        }
        return byteArray;
    }

}