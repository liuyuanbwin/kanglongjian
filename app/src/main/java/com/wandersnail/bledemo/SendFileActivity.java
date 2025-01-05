package com.wandersnail.bledemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import cn.wandersnail.ble.Connection;
import cn.wandersnail.ble.ConnectionState;
import cn.wandersnail.ble.Device;
import cn.wandersnail.ble.EasyBLE;
import cn.wandersnail.ble.EventObserver;
import cn.wandersnail.ble.Request;
import cn.wandersnail.ble.RequestBuilderFactory;
import cn.wandersnail.ble.RequestType;
import cn.wandersnail.ble.WriteCharacteristicBuilder;
import cn.wandersnail.ble.WriteOptions;
import cn.wandersnail.commons.helper.PermissionsRequester2;
import cn.wandersnail.commons.poster.RunOn;
import cn.wandersnail.commons.poster.ThreadMode;
import cn.wandersnail.commons.util.FileUtils;
import cn.wandersnail.commons.util.ToastUtils;

/**
 * date: 2022/8/22 17:17
 * author: zengfansheng
 */
public class SendFileActivity extends BaseActivity {
    private TextView tvPath;
    private Button btnSelectFile;
    private Button btnSend;
    private ProgressBar progressBar;
    private TextView tvPercent;
    private TextView tvProgress;
    private TextView tvState;
    private ImageView imageView;
    private Button btnConvertToRGB;
    private ImageView ivReconstructedImage;
    private RGBPointView rgbPointView;
    private ParcelUuid writeService;
    private ParcelUuid writeCharacteristic;
    private Connection connection;
    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
    private DocumentFile file;
    private File legacyFile;
    private long totalLength;
    private long sentLength;
    private long lastUpdateUiTime;
    private boolean sending;
    private boolean isOldWaySelectFile;
    private final String requestId = UUID.randomUUID().toString();
    private ActivityResultLauncher<Intent> selectFileLauncher;
    private int[] picData;

    private  String filePath;
    
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("发送文件");
        setContentView(R.layout.send_file_activity);
        assignViews();
        Device device = getIntent().getParcelableExtra("DEVICE");
        writeService = getIntent().getParcelableExtra("SERVICE");
        writeCharacteristic = getIntent().getParcelableExtra("CHARACTERISTIC");
        connection = EasyBLE.getInstance().getConnection(device);
        EasyBLE.getInstance().registerObserver(eventObserver);
        progressBar.setMax(10000);
        btnSelectFile.setOnClickListener(v -> {
            if (!doSelect(Intent.ACTION_OPEN_DOCUMENT)) {
                isOldWaySelectFile = true;
                if (!doSelect(Intent.ACTION_GET_CONTENT)) {
                    ToastUtils.showShort("操作失败！当前系统缺少文件选择组件！");
                }
            }
        });
        btnSend.setOnClickListener(v-> {
            btnSend.setEnabled(false);
            progressBar.setProgress(0);
            btnSelectFile.setEnabled(false);
            int[] flattenedArray = this.picData;

            Log.d("onCreate: ",Arrays.toString(flattenedArray));
            byte[] data = this.convertToIntArrayToByteArray(flattenedArray);
            Log.d("SendFileActivity", "data length: " + data.length);
            totalLength = data.length;//file != null ? file.length() : legacyFile.length();
            sending = true;
            new Thread(()-> {
                InputStream input = null;

                int packageSize = 128;//connection.getMtu() - 3;
                byte[] buf = new byte[packageSize];



                input = new ByteArrayInputStream(data);

                try {
                    int len = input.read(buf);
                    if (len != -1) {
                        //先发第一包，成功回调后会从队列取出继续发送
                        WriteCharacteristicBuilder builder = new RequestBuilderFactory().getWriteCharacteristicBuilder(
                                writeService.getUuid(),
                                writeCharacteristic.getUuid(),
                                Arrays.copyOf(buf, len)
                        ).setTag(requestId);
                        builder.setWriteOptions(new WriteOptions.Builder()
                                .setPackageSize(len)
                                .build());
                        connection.execute(builder.build());
                    }
                    while (sending && len != -1) {
                        if (queue.size() > 500) {
                            Thread.sleep(100);
                        } else {
                            queue.add(Arrays.copyOf(buf, len));
                            len = input.read(buf);
                        }
                    }
                    input.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });

        // 新增按钮点击事件
        btnConvertToRGB.setOnClickListener(v -> {
            if (file != null) {
                convertImageToRGB(file.getUri().getPath());
            } else if (legacyFile != null) {
                convertImageToRGB(legacyFile.getAbsolutePath());
            } else {
                ToastUtils.showShort("请先选择图片文件");
            }
        });

        selectFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                if (isOldWaySelectFile) {
                    String path = FileUtils.getFileRealPath(this, result.getData().getData());
                    if (path != null) {
                        File f = new File(path);
                        if (!f.exists()) {
                            ToastUtils.showShort("文件不存在");
                        } else if (updateFileInfo(f.getAbsolutePath(), f.length())) {
                            legacyFile = f;
                        }
                    }
                } else {
                    DocumentFile file = DocumentFile.fromSingleUri(this, result.getData().getData());
                    if (file != null) {
                        if (!file.exists()) {
                            ToastUtils.showShort("文件不存在");
                        } else {
                            String path = FileUtils.getFileRealPath(this, result.getData().getData());
                            if (path == null) {
                                path = result.getData().getData().toString();
                            }
                            if (updateFileInfo(path, file.length())) {
                                this.filePath = path;
                                this.file = file;
                            }
                        }
                    }
                }
            }
        });

        //动态申请权限
        PermissionsRequester2 permissionsRequester = new PermissionsRequester2(this);
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissionsRequester.checkAndRequest(list);
    }

    private void assignViews() {
        tvPath = findViewById(R.id.tvPath);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnSend = findViewById(R.id.btnSend);
        progressBar = findViewById(R.id.progressBar);
        tvPercent = findViewById(R.id.tvPercent);
        tvProgress = findViewById(R.id.tvProgress);
        tvState = findViewById(R.id.tvState);
        imageView = findViewById(R.id.imageView);
        btnConvertToRGB = findViewById(R.id.btnConvertToRGB);
        ivReconstructedImage = findViewById(R.id.ivReconstructedImage);
        rgbPointView = findViewById(R.id.rgbPointView); // 初始化 rgbPointView
    }


    private byte[] convertToIntArrayToByteArray(int[] intArray) {

        // 每个int值将转换为4个字节，所以byte数组的大小是int数组大小的4倍
        byte[] byteArray = new byte[intArray.length * 4];

        for (int i = 0; i < intArray.length; i++) {
            int intValue = intArray[i];
            // 将int值转换为4个字节，并存储到byte数组中
            for (int j = 0; j < 4; j++) {
                byteArray[i * 4 + j] = (byte) (intValue >> ((3 - j) * 8));
            }
        }

        return byteArray;
    }
    
    private boolean updateFileInfo(String path, long len) {
        if (len <= 0) {
            ToastUtils.showShort("请选择非空文件");
            return false;
        }
        String root = "";
        try {
            root = Environment.getExternalStorageDirectory().getAbsolutePath();
        } catch (Throwable ignore) {}
        tvPath.setText(root.length() > 0 ? path.replace(root, "内部存储") : path);
        btnSend.setEnabled(true);
        totalLength = len;
        sentLength = 0;
        tvState.setText("");
        updateProgress();

        // 检查文件是否为图片并加载
        if (isImageFile(path)) {
            loadImage(path);
        } else {
            imageView.setImageDrawable(null); // 清除 ImageView
        }

        return true;
    }

    // 添加检查文件是否为图片的方法
    private boolean isImageFile(String path) {
        String mimeType = null;
        if (path != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            int index = path.lastIndexOf('.');
            if (index > -1) {
                String extension = path.substring(index + 1).toLowerCase();
                mimeType = mime.getMimeTypeFromExtension(extension);
            }
        }
        return mimeType != null && mimeType.startsWith("image/");
    }

    // 添加加载图片的方法
    private void loadImage(String path) {
        // 从文件路径加载图片并缩放到128x128
        Bitmap bitmap = decodeSampledBitmapFromFile(path, 128, 128);
        // 将图片设置到 ImageView
        imageView.setImageBitmap(bitmap);
    }

    // 添加缩放图片的方法
    private Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        // 第一次解码，只获取图片的宽高信息
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // 计算缩放比例
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 第二次解码，加载缩放后的图片
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        // 将图片缩放到实际尺寸 128x128 像素
        return Bitmap.createScaledBitmap(bitmap, 128, 128, true);
    }

    // 添加计算缩放比例的方法
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 图片的原始宽高
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 计算最大的 inSampleSize 值，使得宽高都小于等于请求的宽高
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    // SendFileActivity.java
    private void convertImageToRGB(String imagePath) {

        Bitmap bitmap = decodeSampledBitmapFromFile(this.filePath, 128, 128);
        if (bitmap == null) {
            ToastUtils.showShort("图片加载111失败");
            return;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // 重新绘制图片
        Bitmap reconstructedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        reconstructedBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        ivReconstructedImage.setImageBitmap(reconstructedBitmap);

        // 将一维像素数组转换为二维数组
        int[][] rgbData = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                rgbData[i][j] = pixels[i * width + j];
            }
        }

        // 传递 RGB 数据给 rgbPointView
        rgbPointView.setRGBData(rgbData);
        this.picData = pixels;

    }



    private final EventObserver eventObserver = new EventObserver() {
        @RunOn(ThreadMode.MAIN)
        @Override
        public void onConnectionStateChanged(@NonNull Device device) {
            if (device.getConnectionState() != ConnectionState.SERVICE_DISCOVERED) {
                tvState.setText("连接断开");
                sending = false;
            }
        }

        @RunOn(ThreadMode.BACKGROUND)
        @Override
        public void onCharacteristicWrite(@NonNull Request request, @NonNull byte[] value) {
            if (sending && requestId.equals(request.getTag())) {
                sentLength += value.length;
                if (queue.isEmpty()) {
                    runOnUiThread(()-> tvState.setText("发送完成"));
                } else {
                    byte[] bytes = queue.remove();
                    WriteCharacteristicBuilder builder = new RequestBuilderFactory().getWriteCharacteristicBuilder(
                            writeService.getUuid(),
                            writeCharacteristic.getUuid(),
                            bytes
                    ).setTag(requestId);
                    builder.setWriteOptions(new WriteOptions.Builder()
                            .setPackageSize(bytes.length)
                            .build());
                    connection.execute(builder.build());
                }
                updateProgress();
            }
        }

        @RunOn(ThreadMode.MAIN)
        @Override
        public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {
            if (sending && requestId.equals(request.getTag()) && request.getType() == RequestType.WRITE_CHARACTERISTIC) {
                sending = false;
                btnSend.setEnabled(true);
                btnSelectFile.setEnabled(true);
                tvState.setText("发送失败");
            }
        }
    };
    
    private boolean doSelect(String action) {
        try {
            //type和category都必须写，否则无法调起，还会抛异常
            Intent intent = new Intent(action);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType( "*/*");
            selectFileLauncher.launch(intent);
        } catch (Throwable t) {
            return false;
        }
        return true;
    }
    
    private void updateProgress() {
        if (System.currentTimeMillis() - lastUpdateUiTime < 200) {
            return;
        }
        lastUpdateUiTime = System.currentTimeMillis();
        runOnUiThread(()-> {
            tvProgress.setText(sentLength + "/" + totalLength);
            float percent = totalLength == 0 ? 0 : sentLength * 1f / totalLength;
            tvPercent.setText(new DecimalFormat("#0.00").format(percent * 100) + "%");
            progressBar.setProgress((int) (percent * progressBar.getMax()));
            if (totalLength > 0 && totalLength <= sentLength) {
                sending = false;
                btnSend.setEnabled(true);
                btnSelectFile.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EasyBLE.getInstance().unregisterObserver(eventObserver);
    }
}
