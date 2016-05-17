package com.corcow.hw.flagproject.activity.main;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.corcow.hw.flagproject.R;
import com.corcow.hw.flagproject.util.Utilities;

import org.askerov.dynamicgrid.DynamicGridView;

import java.io.File;

/**
 * Created by multimedia on 2016-05-17.
 */
public class FileSelectDialog extends DialogFragment {

    /*--- Click Event Handler ---*/
    // BackKey 두번 누르면 종료
    boolean isBackPressed = false;
    private static final int MESSAGE_BACKKEY_TIMEOUT = 1;           // Handler message
    private static final int TIMEOUT_BACKKEY_DELAY = 2000;          // timeout delay
    // 더블클릭 시 파일 실행
    boolean isFirstClicked = false;
    private static final int MESSAGE_FILE_OPEN_TIMEOUT = 2;         // Handler message
    private static final int TIMEOUT_FILE_OPEN_DELAY = 1000;        // timeout delay
    // Timeout handler
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_BACKKEY_TIMEOUT :
                    isBackPressed = false;              // 시간이 지나도 안눌리면 false로
                    break;
                case MESSAGE_FILE_OPEN_TIMEOUT :
                    isFirstClicked = false;
                    break;
            }
        }
    };

    // Views
    DynamicGridView fileGridView;
    TextView currentPathView;
    FileGridAdpater mAdapter;
    Button selectBtn, cancelBtn;

    // Variables
    int originalPosition = -1;           // in Edit mode
    int draggingPosition = -1;           // in Edit mode
    String rootPath;                     // SD card root storage path   ... back 키 조작 시 참조
    String currentPath;                  // current path (현재 경로)

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Dialog dlg = getDialog();
        int width = getResources().getDimensionPixelSize(R.dimen.fileselect_dlalog_width);
        int height = getResources().getDimensionPixelSize(R.dimen.fileselect_dlalog_height);
        getDialog().getWindow().setLayout(width, height);
        dlg.getWindow().setLayout(width, height);
        WindowManager.LayoutParams params = dlg.getWindow().getAttributes();
        dlg.getWindow().setAttributes(params);

        setCancelable(false);       // back key 제어

        getDialog().getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;


    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_select, container);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        // View Initialize
        selectBtn = (Button)view.findViewById(R.id.btn_select);
        cancelBtn = (Button)view.findViewById(R.id.btn_cancel);
        fileGridView = (DynamicGridView)view.findViewById(R.id.fileGridView);
        currentPathView = (TextView)view.findViewById(R.id.currentPathView);
        mAdapter = new FileGridAdpater(getActivity(), fileGridView.getNumColumns());
        fileGridView.setAdapter(mAdapter);

        // 시작은 최상위 root directory.
        rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        currentPath = rootPath;               // current path 를 root directory로
        showFileList(currentPath);

        // FileGridView listeners
        fileGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 더블클릭 시 실행 (isFileSelected로 확인)
                if (!isFirstClicked) {
                    isFirstClicked = true;
                    // Toast.makeText(MainActivity.this, "한번 더 누르면 실행됩니다.", Toast.LENGTH_SHORT).show();
                    mHandler.sendEmptyMessageDelayed(MESSAGE_FILE_OPEN_TIMEOUT, TIMEOUT_FILE_OPEN_DELAY);           // TIMEOUT_FILE_OPEN_DELAY (1초) 기다림
                } else {
                    // TIMEOUT_FILE_OPEN_DELAY 안에 또 눌린경우 (더블터치 한 경우)
                    mHandler.removeMessages(MESSAGE_FILE_OPEN_TIMEOUT);
                    isFirstClicked = false;
                    // 동작
                    String selectedPath = ((FileItem) mAdapter.getItem(position)).absolutePath;
                    File selectedFile = new File(selectedPath);
                    if (selectedFile.isDirectory()) {
                        // 선택된 item이 폴더인 경우
                        currentPath = selectedFile.getAbsolutePath();       // 경로 갱신
                        showFileList(currentPath);                          // view 갱신
                    } else {
                        // 선택된 item이 파일인 경우
                        Utilities.openFile(getActivity(), selectedFile);                             // 파일 실행
                    }

                }
            }
        });
        fileGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // LongClick 시 편집 모드 시작 (Drag Start)
                fileGridView.startEditMode(position);
                return true;
            }
        });
        fileGridView.setOnDragListener(new DynamicGridView.OnDragListener() {
            @Override
            public void onDragStarted(int position) {
                // Drag 시작 위치 저장
                originalPosition = position;
            }

            @Override
            public void onDragPositionsChanged(int oldPosition, int newPosition) {
                // Drag position이 변화 시 draggingPosition 갱신
                draggingPosition = newPosition;
            }
        });
        fileGridView.setOnDropListener(new DynamicGridView.OnDropListener() {
            @Override
            public void onActionDrop() {
                // Drop 시 draggingPosition에 grid 아이템이 존재한다면, (draggingPosition != -1)
                // Drop position의 아이템이 파일인지 폴더인지 판별.
                // 폴더라면 해당 폴더로 파일이 이동된다.  /  파일이라면 아무일 안생김.
                if (draggingPosition != -1 && draggingPosition != originalPosition) {
                    File droppedFile = new File(((FileItem) mAdapter.getItem(draggingPosition)).absolutePath);
                    if (droppedFile.isDirectory()) {
                        Utilities.moveFile(((FileItem) mAdapter.getItem(originalPosition)).absolutePath, droppedFile.getAbsolutePath());
                        mAdapter.delete(originalPosition);          // GridView 에서도 지워준다.
                    }
                }

                // Drop 시 Editmode는 종료, Drag를 시작한 position, Edit중인 current position을 초기화
                fileGridView.stopEditMode();
                originalPosition = -1;
                draggingPosition = -1;
            }
        });


        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 선택된 파일을 Parent Fragment로
                Toast.makeText(getContext(), "파일을 선택해 주세요", Toast.LENGTH_SHORT).show();
            }
        });

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // dismiss
                FileSelectDialog.this.dismiss();
            }
        });

        return view;

    }


    /** showFileList() ... 인자로 받은 path에 있는 파일들을 GridView에 띄우는 함수
     * @param currentPath   :   currentPath를 갱신 후에 인자로 넣을 것.
     */
    private void showFileList(String currentPath) {
        currentPathView.setText(currentPath);        // 현재 Path 를 보여줌
        File currentDir = new File(currentPath);
        File[] files = currentDir.listFiles();       // 현재 경로의 File 리스트를 받아옴

        // add items to adapter
        mAdapter.clear();
        for (File f : files) {
            FileItem item = new FileItem(f.getName(), f.getAbsolutePath());
            if (f.isDirectory()) {
                item.iconImgResource = R.drawable.folder;
            } else if (item.extension.equalsIgnoreCase("jpg") || item.extension.equalsIgnoreCase("jpeg")
                    || item.extension.equalsIgnoreCase("png") || item.extension.equalsIgnoreCase("bmp")
                    || item.extension.equalsIgnoreCase("gif")) {
                item.iconImgResource = FileItem.IS_IMAGE_FILE;
            } else if (item.extension.equalsIgnoreCase("avi") || item.extension.equalsIgnoreCase("mp4")) {
                item.iconImgResource = FileItem.IS_VIDEO_FILE;
            } else if (item.extension.equalsIgnoreCase("hwp")) {
                item.iconImgResource = R.drawable.icon_file_hwp;
            } else if (item.extension.equalsIgnoreCase("ppt") || (item.extension.equalsIgnoreCase("pptx"))) {
                item.iconImgResource = R.drawable.icon_file_ppt;
            } else if (item.extension.equalsIgnoreCase("xls") || item.extension.equalsIgnoreCase("xlsx")
                    || item.extension.equalsIgnoreCase("xlsm")) {
                item.iconImgResource = R.drawable.icon_file_xls;
            } else if (item.extension.equalsIgnoreCase("pdf")) {
                item.iconImgResource = R.drawable.icon_file_pdf;
            } else {
                item.iconImgResource = R.drawable.file;
            }

            if (!f.getName().startsWith(".")) {
                mAdapter.add(item);
            }
        }
    }

}
