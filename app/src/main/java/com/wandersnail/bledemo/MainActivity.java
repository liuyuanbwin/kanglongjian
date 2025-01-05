package com.wandersnail.bledemo;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.wang.avi.AVLoadingIndicatorView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.wandersnail.adapter.tree.Node;
import cn.wandersnail.adapter.tree.TreeListAdapter;
import cn.wandersnail.ble.Connection;
import cn.wandersnail.ble.ConnectionConfiguration;
import cn.wandersnail.ble.Device;
import cn.wandersnail.ble.EasyBLE;
import cn.wandersnail.ble.EasyBLEBuilder;
import cn.wandersnail.ble.Request;
import cn.wandersnail.ble.RequestBuilder;
import cn.wandersnail.ble.RequestBuilderFactory;
import cn.wandersnail.ble.WriteCharacteristicBuilder;
import cn.wandersnail.ble.WriteOptions;
import cn.wandersnail.ble.callback.IndicationChangeCallback;
import cn.wandersnail.ble.callback.MtuChangeCallback;
import cn.wandersnail.ble.callback.NotificationChangeCallback;
import cn.wandersnail.ble.callback.ReadCharacteristicCallback;
import cn.wandersnail.commons.observer.Observe;
import cn.wandersnail.commons.poster.RunOn;
import cn.wandersnail.commons.poster.Tag;
import cn.wandersnail.commons.poster.ThreadMode;
import cn.wandersnail.commons.util.StringUtils;
import cn.wandersnail.commons.util.ToastUtils;

/**
 * date: 2019/8/2 23:33
 * author: zengfansheng
 */
public class MainActivity extends BaseActivity {
    private Device device;
    private ListView lv;
    private FrameLayout layoutConnecting;
    private AVLoadingIndicatorView loadingIndicator;
    private ImageView ivDisconnected;
    private final List<Item> itemList = new ArrayList<>();
    private ListViewAdapter adapter;
    private Connection connection;

    private void assignViews() {
        lv = findViewById(R.id.lv);
        layoutConnecting = findViewById(R.id.layoutConnecting);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        ivDisconnected = findViewById(R.id.ivDisconnected);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        device = getIntent().getParcelableExtra("device");
        setContentView(R.layout.activity_main);
        assignViews();
        initViews();
        //连接配置，举个例随意配置两项
        ConnectionConfiguration config = new ConnectionConfiguration();
        config.setConnectTimeoutMillis(10000);
        config.setRequestTimeoutMillis(1000);
        config.setAutoReconnect(false);
//        connection = EasyBLE.getInstance().connect(device, config, observer);//回调监听连接状态，设置此回调不影响观察者接收连接状态消息
        connection = EasyBLE.getInstance().connect(device, config);//观察者监听连接状态  
        connection.setBluetoothGattCallback(new BluetoothGattCallback() {
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("EasyBLE", "原始写入数据：" + StringUtils.toHex(characteristic.getValue()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //释放连接
        EasyBLE.getInstance().releaseConnection(device);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (device != null && !device.isDisconnected()) {
            menu.findItem(R.id.menuDisconnect).setVisible(true);
            menu.findItem(R.id.menuConnect).setVisible(false);
            menu.findItem(R.id.menuAutoConnect).setVisible(false);
        } else {
            menu.findItem(R.id.menuDisconnect).setVisible(false);
            menu.findItem(R.id.menuConnect).setVisible(true);
            menu.findItem(R.id.menuAutoConnect).setVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuDisconnect:
                EasyBLE.getInstance().disconnectConnection(device);
                break;
            case R.id.menuConnect: {
                ConnectionConfiguration config = new ConnectionConfiguration();
                config.setConnectTimeoutMillis(10000);
                config.setRequestTimeoutMillis(1000);
                config.setAutoReconnect(false);
                config.setUseAutoConnect(false);
                connection = EasyBLE.getInstance().connect(device, config);//观察者监听连接状态
            }
            break;
            case R.id.menuAutoConnect: {
                ConnectionConfiguration config = new ConnectionConfiguration();
                config.setConnectTimeoutMillis(10000);
                config.setRequestTimeoutMillis(1000);
                config.setAutoReconnect(false);
                config.setUseAutoConnect(true);
                connection = EasyBLE.getInstance().connect(device, config);//观察者监听连接状态
            }
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectFailed(@NonNull Device device, int failType) {
        switch (failType) {
            case Connection.CONNECT_FAIL_TYPE_LACK_CONNECT_PERMISSION:
                ToastUtils.showShort("连接失败：缺少连接权限");
                break;
            case Connection.CONNECT_FAIL_TYPE_CONNECTION_IS_UNSUPPORTED:
                ToastUtils.showShort("连接失败：设备不支持连接");
                break;
            case Connection.CONNECT_FAIL_TYPE_MAXIMUM_RECONNECTION:
                ToastUtils.showShort("连接失败：达到最大重连次数限制");
                break;
        }
    }

    @Override
    public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {
        if (gattStatus != -1) {
            switch (gattStatus) {
                case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                    ToastUtils.showShort("请求不支持");
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 使用{@link Observe}确定要接收消息，{@link RunOn}指定在主线程执行方法，设置{@link Tag}防混淆后找不到方法
     */
    @Tag("onConnectionStateChanged")
    @Observe
    @RunOn(ThreadMode.MAIN)
    @Override
    public void onConnectionStateChanged(@NonNull Device device) {
        Log.d("EasyBLE", "主线程：" + (Looper.getMainLooper() == Looper.myLooper()) + ", 连接状态：" + device.getConnectionState());
        switch (device.getConnectionState()) {
            case SCANNING_FOR_RECONNECTION:
                ivDisconnected.setVisibility(View.INVISIBLE);
                break;
            case CONNECTING:
                layoutConnecting.setVisibility(View.VISIBLE);
                loadingIndicator.setVisibility(View.VISIBLE);
                ivDisconnected.setVisibility(View.INVISIBLE);
                break;
            case DISCONNECTED:
                layoutConnecting.setVisibility(View.VISIBLE);
                loadingIndicator.setVisibility(View.INVISIBLE);
                ivDisconnected.setVisibility(View.VISIBLE);
                break;
            case SERVICE_DISCOVERED:
                layoutConnecting.setVisibility(View.INVISIBLE);
                loadingIndicator.setVisibility(View.INVISIBLE);
                itemList.clear();
                int id = 0;
                List<BluetoothGattService> services = connection.getGatt().getServices();
                for (BluetoothGattService service : services) {
                    int pid = id;
                    Item item = new Item(pid, 0, 0);
                    item.isService = true;
                    item.service = service;
                    itemList.add(item);
                    id++;
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        Item i = new Item(id++, pid, 1);
                        i.service = service;
                        i.characteristic = characteristic;
                        itemList.add(i);
                    }
                }
                adapter.notifyDataSetChanged();
                //设置MTU
                Connection connection = EasyBLE.getInstance().getConnection(device);
                RequestBuilder<MtuChangeCallback> builder = new RequestBuilderFactory().getChangeMtuBuilder(503);
                Request request = builder.setCallback(new MtuChangeCallback() {
                    @Override
                    public void onMtuChanged(@NonNull Request request, int mtu) {
                        Log.d("EasyBLE", "MTU修改成功，新值：" + mtu);
                    }

                    @Override
                    public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {

                    }
                }).build();
                connection.execute(request);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        connection.execute(new RequestBuilderFactory().getSetPreferredPhyBuilder(2, 2, 0).build());
                    }, 300);
                }
                break;
        }
        invalidateOptionsMenu();
    }

    /**
     * 使用{@link Observe}确定要接收消息，方法在{@link EasyBLEBuilder#setMethodDefaultThreadMode(ThreadMode)}指定的线程执行
     */
    @Observe
    @Override
    public void onNotificationChanged(@NonNull Request request, boolean isEnabled) {
        Log.d("EasyBLE", "主线程：" + (Looper.getMainLooper() == Looper.myLooper()) + ", 通知/Indication：" + (isEnabled ? "开启" : "关闭"));
        if (isEnabled) {
            ToastUtils.showShort("Notification开启了");
        } else {
            ToastUtils.showShort("Notification关闭了");
        }
    }

    @Override
    public void onIndicationChanged(@NonNull Request request, boolean isEnabled) {
        if (isEnabled) {
            ToastUtils.showShort("Indication开启了");
        } else {
            ToastUtils.showShort("Indication关闭了");
        }
    }

    /**
     * 如果{@link EasyBLEBuilder#setObserveAnnotationRequired(boolean)}设置为false时，无论加不加{@link Observe}注解都会收到消息。
     * 设置为true时，必须加{@link Observe}才会收到消息。
     * 默认为false，方法默认执行线程在{@link EasyBLEBuilder#setMethodDefaultThreadMode(ThreadMode)}指定
     */
    @Override
    public void onCharacteristicWrite(@NonNull Request request, @NonNull byte[] value) {
        Log.d("EasyBLE", "主线程：" + (Looper.getMainLooper() == Looper.myLooper()) + ", 成功写入：" + StringUtils.toHex(value, " "));
        if ("single_write_test".equals(request.getTag())) {
            ToastUtils.showShort("成功写入：" + StringUtils.toHex(value, " "));
        }
    }

    /**
     * notification数据
     *
     * @param device         设备
     * @param service        服务UUID
     * @param characteristic 特征UUID
     * @param value          数据
     */
    @Override
    public void onCharacteristicChanged(@NonNull Device device, @NonNull UUID service, @NonNull UUID characteristic, @NonNull byte[] value) {
        Log.d("EasyBLE", "主线程：" + (Looper.getMainLooper() == Looper.myLooper()) + ", 收到设备主动发送来的数据：" + StringUtils.toHex(value, " "));
    }

    private void initViews() {
        adapter = new ListViewAdapter(lv, itemList);
        adapter.setOnInnerItemClickListener((item, adapterView, view, i) -> {
            final List<String> menuItems = new ArrayList<>();
            if (item.hasNotifyProperty) {
                boolean isEnabled = connection.isNotificationEnabled(item.service.getUuid(), item.characteristic.getUuid());
                if (isEnabled) {
                    menuItems.add("关闭Notification");
                } else {
                    menuItems.add("开启Notification");
                }
            }
            if (item.hasIndicateProperty) {
                boolean isEnabled = connection.isIndicationEnabled(item.service.getUuid(), item.characteristic.getUuid());
                if (isEnabled) {
                    menuItems.add("关闭Indication");
                } else {
                    menuItems.add("开启Indication");
                }
            }
            if (item.hasReadProperty) {
                menuItems.add("读取特征值");
            }
            if (item.hasWriteProperty) {
                menuItems.add("写入测试数据");
                menuItems.add("清空文件");
                menuItems.add("打印文件列表");

                menuItems.add("发送文件");
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setItems(menuItems.toArray(new String[0]), (dialog, which) -> {
                        dialog.dismiss();
                        switch (menuItems.get(which)) {
                            case "关闭Notification":
                            case "开启Notification":
                                setNotification(item);
                                break;
                            case "开启Indication":
                            case "关闭Indication":
                                setIndication(item);
                                break;
                            case "读取特征值":
                                readCharacteristic(item);
                                break;
                            case "发送文件":
                                Intent intent = new Intent(this, SendFileActivity.class);
                                intent.putExtra("DEVICE", device);
                                intent.putExtra("SERVICE", new ParcelUuid(item.service.getUuid()));
                                intent.putExtra("CHARACTERISTIC", new ParcelUuid(item.characteristic.getUuid()));
                                startActivity(intent);
                                break;
                            case "清空文件":
                                writeCharacteristic(item, new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff});
                                break;
                            case "打印文件列表":
                                writeCharacteristic(item, new byte[]{(byte) 0xaa, (byte) 0xaa, (byte) 0xaa});
                                break;
                            default:
                                String str = "Multi-pass deformation also shows that in high-temperature rolling process, the material will be softened as a result of the recovery and recrystallization, so the rolling force is reduced and the time interval of the passes of rough rolling should be longer.";

                                // 将字符串转换为 byte 数组
                                byte[] data = str.getBytes();
                                writeCharacteristic(item, data);
                                break;
                        }
                    })
                    .show();
        });
    }

    private void writeCharacteristic(@NotNull Item item, byte[] data) {
        Log.d("EasyBLE", "开始写入");
        WriteCharacteristicBuilder builder = new RequestBuilderFactory().getWriteCharacteristicBuilder(item.service.getUuid(),
                item.characteristic.getUuid(), data);
        builder.setTag("single_write_test");
        //根据需要设置写入配置
        int writeType = connection.hasProperty(item.service.getUuid(), item.characteristic.getUuid(),
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) ?
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        builder.setWriteOptions(new WriteOptions.Builder()
                .setPackageSize(connection.getMtu() - 3)
                .setPackageWriteDelayMillis(5)
                .setRequestWriteDelayMillis(10)
                .setWaitWriteResult(true)
                .setWriteType(writeType)
                .build());
        //不设置回调，使用观察者模式接收结果
        builder.build().execute(connection);
    }

    private void readCharacteristic(@NotNull Item item) {
        RequestBuilder<ReadCharacteristicCallback> builder = new RequestBuilderFactory().getReadCharacteristicBuilder(item.service.getUuid(), item.characteristic.getUuid());
        builder.setTag(UUID.randomUUID().toString());
        builder.setPriority(Integer.MAX_VALUE);//设置请求优先级
        //设置了回调则观察者不会收到此次请求的结果消息
        builder.setCallback(new ReadCharacteristicCallback() {
            //注解可以指定回调线程
            @RunOn(ThreadMode.BACKGROUND)
            @Override
            public void onCharacteristicRead(@NonNull Request request, @NonNull byte[] value) {
                Log.d("EasyBLE", "主线程：" + (Looper.getMainLooper() == Looper.myLooper()) + ", 读取到特征值：" + StringUtils.toHex(value, " "));
                ToastUtils.showShort("读取到特征值：" + StringUtils.toHex(value, " "));
            }

            //不使用注解指定线程的话，使用构建器设置的默认线程
            @Override
            public void onRequestFailed(@NonNull Request request, int failType, @Nullable Object value) {

            }

            @Override
            public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {

            }
        });
        builder.build().execute(connection);
    }

    private void setNotification(@NotNull Item item) {
        boolean isEnabled = connection.isNotificationEnabled(item.service.getUuid(), item.characteristic.getUuid());
        RequestBuilder<NotificationChangeCallback> builder = new RequestBuilderFactory().getSetNotificationBuilder(item.service.getUuid(), item.characteristic.getUuid(), !isEnabled);
        //不设置回调，使用观察者模式接收结果
        builder.build().execute(connection);
    }

    private void setIndication(@NotNull Item item) {
        boolean isEnabled = connection.isIndicationEnabled(item.service.getUuid(), item.characteristic.getUuid());
        RequestBuilder<IndicationChangeCallback> builder = new RequestBuilderFactory().getSetIndicationBuilder(item.service.getUuid(), item.characteristic.getUuid(), !isEnabled);
        //不设置回调，使用观察者模式接收结果
        builder.build().execute(connection);
    }

    private class ListViewAdapter extends TreeListAdapter<Item> {
        ListViewAdapter(@NotNull ListView lv, @NotNull List<Item> nodes) {
            super(lv, nodes);
        }

        @NotNull
        @Override
        protected Holder<Item> getHolder(int i) {
            //根据位置返回不同布局
            int type = getItemViewType(i);
            if (type == 1) {//服务
                return new Holder<Item>() {
                    private ImageView iv;
                    private TextView tvUuid;

                    @Override
                    public void onBind(Item item, int position) {
                        iv.setVisibility(item.hasChild() ? View.VISIBLE : View.INVISIBLE);
                        iv.setBackgroundResource(item.isExpand() ? R.drawable.expand : R.drawable.fold);
                        tvUuid.setText(item.service.getUuid().toString());
                    }

                    @NotNull
                    @Override
                    public View createView() {
                        View view = View.inflate(MainActivity.this, R.layout.item_service, null);
                        iv = view.findViewById(R.id.ivIcon);
                        tvUuid = view.findViewById(R.id.tvUuid);
                        return view;
                    }
                };
            } else {
                return new Holder<Item>() {
                    private TextView tvUuid;
                    private TextView tvProperty;

                    @Override
                    public void onBind(Item item, int i) {
                        tvUuid.setText(item.characteristic.getUuid().toString());
                        //获取权限列表
                        tvProperty.setText(getPropertiesString(item));
                    }

                    @NotNull
                    @Override
                    public View createView() {
                        View view = View.inflate(MainActivity.this, R.layout.item_characteristic, null);
                        tvUuid = view.findViewById(R.id.tvUuid);
                        tvProperty = view.findViewById(R.id.tvProperty);
                        return view;
                    }
                };
            }
        }

        private String getPropertiesString(Item node) {
            StringBuilder sb = new StringBuilder();
            int[] properties = new int[]{BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PROPERTY_INDICATE,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE};
            String[] propertyStrs = new String[]{"WRITE", "INDICATE", "NOTIFY", "READ", "SIGNED_WRITE", "WRITE_NO_RESPONSE"};
            for (int i = 0; i < properties.length; i++) {
                int property = properties[i];
                if ((node.characteristic.getProperties() & property) != 0) {
                    if (sb.length() != 0) {
                        sb.append(", ");
                    }
                    sb.append(propertyStrs[i]);
                    if (property == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
                        node.hasNotifyProperty = true;
                    }
                    if (property == BluetoothGattCharacteristic.PROPERTY_INDICATE) {
                        node.hasIndicateProperty = true;
                    }
                    if (property == BluetoothGattCharacteristic.PROPERTY_WRITE || property == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                        node.hasWriteProperty = true;
                    }
                    if (property == BluetoothGattCharacteristic.PROPERTY_READ) {
                        node.hasReadProperty = true;
                    }
                }
            }
            return sb.toString();
        }

        @Override
        public int getViewTypeCount() {
            return super.getViewTypeCount() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).isService ? 1 : 0;
        }
    }

    private static class Item extends Node<Item> {
        boolean isService;
        BluetoothGattService service;
        BluetoothGattCharacteristic characteristic;
        boolean hasNotifyProperty;
        boolean hasIndicateProperty;
        boolean hasWriteProperty;
        boolean hasReadProperty;

        Item(int id, int pId, int level) {
            super(id, pId, level);
        }
    }
}
