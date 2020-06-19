package com.esgi4al.paulin.kfileexplorer.main

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.BottomSheetDialog
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.esgi4al.paulin.kfileexplorer.R
import com.esgi4al.paulin.kfileexplorer.common.FileType
import com.esgi4al.paulin.kfileexplorer.fileservice.FileChangeBroadcastReceiver
import com.esgi4al.paulin.kfileexplorer.fileservice.FileIntentService
import com.esgi4al.paulin.kfileexplorer.fileslist.FilesListFragment
import com.esgi4al.paulin.kfileexplorer.models.FileModel
import com.esgi4al.paulin.kfileexplorer.utils.createNewFile
import com.esgi4al.paulin.kfileexplorer.utils.createNewFolder
import com.esgi4al.paulin.kfileexplorer.utils.createShortSnackbar
import com.esgi4al.paulin.kfileexplorer.utils.launchFileIntent
import kotlinx.android.synthetic.main.activity_main.*
import com.esgi4al.paulin.kfileexplorer.utils.deleteFile as FileUtilsDeleteFile
import kotlinx.android.synthetic.main.dialog_enter_name.view.*

class MainActivity : AppCompatActivity(), FilesListFragment.OnItemClickListener {

    companion object {
        private const val OPTIONS_DIALOG_TAG: String = "com.esgi4al.paulin.kfileexplorer.main.options_dialog"
    }

    private val backStackManager = BackStackManager()
    private lateinit var mBreadcrumbRecyclerAdapter: BreadcrumbRecyclerAdapter
    private var isCopyModeActive: Boolean = false
    private var selectedFileModel: FileModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.or(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            val filesListFragment = FilesListFragment.build {
                path = Environment.getExternalStorageDirectory().absolutePath
            }

            supportFragmentManager.beginTransaction()
                    .add(R.id.container, filesListFragment)
                    .addToBackStack(Environment.getExternalStorageDirectory().absolutePath)
                    .commit()

        }

        requestPermissions()
        initViews()
        initBackStack()
    }

    private fun initViews() {
        setSupportActionBar(toolbar)

        breadcrumbRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mBreadcrumbRecyclerAdapter = BreadcrumbRecyclerAdapter()
        breadcrumbRecyclerView.adapter = mBreadcrumbRecyclerAdapter
        mBreadcrumbRecyclerAdapter.onItemClickListener = {
            supportFragmentManager.popBackStack(it.path, 2);
            backStackManager.popFromStackTill(it)
        }
    }

    private fun initBackStack() {
        backStackManager.onStackChangeListener = {
            updateAdapterData(it)
        }

        backStackManager.addToStack(fileModel = FileModel(Environment.getExternalStorageDirectory().absolutePath, FileType.FOLDER, "/", 0.0))
    }



    override fun onClick(fileModel: FileModel) {
        if (fileModel.fileType == FileType.FOLDER) {
            addFileFragment(fileModel)
        } else {
            launchFileIntent(fileModel)
        }
    }

    override fun onLongClick(fileModel: FileModel) {
        val optionsDialog = FileOptionsDialog.build {}

        optionsDialog.onDeleteClickListener = {
            FileUtilsDeleteFile(fileModel.path)
            updateContentOfCurrentFragment()
            coordinatorLayout.createShortSnackbar("'${fileModel.name}' deleted successfully.")
        }

        optionsDialog.onCopyClickListener = {
            isCopyModeActive = true
            selectedFileModel = fileModel
            invalidateOptionsMenu()
        }

        optionsDialog.show(supportFragmentManager, OPTIONS_DIALOG_TAG)
    }




    private fun addFileFragment(fileModel: FileModel) {
        val filesListFragment = FilesListFragment.build {
            path = fileModel.path
        }


        backStackManager.addToStack(fileModel)

        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
        fragmentTransaction.replace(R.id.container, filesListFragment)
        fragmentTransaction.addToBackStack(fileModel.path)
        fragmentTransaction.commit()
    }


    private fun updateAdapterData(files: List<FileModel>) {
        mBreadcrumbRecyclerAdapter.updateData(files)
        if (files.isNotEmpty()) {
            breadcrumbRecyclerView.smoothScrollToPosition(files.size - 1)
        }
    }


    override fun onBackPressed() {
        if (isCopyModeActive) {
            isCopyModeActive = false
            invalidateOptionsMenu()
            return
        }

        super.onBackPressed()
        backStackManager.popFromStack()
        if (supportFragmentManager.backStackEntryCount == 0) {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val subMenu = menu?.findItem(R.id.subMenu)
        val pasteItem = menu?.findItem(R.id.menuPasteFile)
        val cancelItem = menu?.findItem(R.id.menuCancel)

        subMenu?.isVisible = !isCopyModeActive
        pasteItem?.isVisible = isCopyModeActive
        cancelItem?.isVisible = isCopyModeActive

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menuNewFile -> createNewFileInCurrentDirectory()
            R.id.menuNewFolder -> createNewFolderInCurrentDirectory()
            R.id.menuCancel -> {
                isCopyModeActive = false
                invalidateOptionsMenu()
            }
            R.id.menuPasteFile -> {
                val intent = Intent(this, FileIntentService::class.java)
                intent.action = FileIntentService.ACTION_COPY
                intent.putExtra(FileIntentService.EXTRA_FILE_SOURCE_PATH, selectedFileModel?.path)
                intent.putExtra(FileIntentService.EXTRA_FILE_DESTINATION_PATH, backStackManager.top.path)
                startService(intent)

                isCopyModeActive = false
                invalidateOptionsMenu()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNewFileInCurrentDirectory() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_enter_name, null)
        view.createButton.setOnClickListener {
            val fileName = view.nameEditText.text.toString()
            if (fileName.isNotEmpty()) {
                createNewFile(fileName, backStackManager.top.path) { _, message ->
                    bottomSheetDialog.dismiss()
                    coordinatorLayout.createShortSnackbar(message)
                    updateContentOfCurrentFragment()
                }
            }
        }
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun createNewFolderInCurrentDirectory() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_enter_name, null)
        view.createButton.setOnClickListener {
            val fileName = view.nameEditText.text.toString()
            if (fileName.isNotEmpty()) {
                createNewFolder(fileName, backStackManager.top.path) { _, message ->
                    bottomSheetDialog.dismiss()
                    coordinatorLayout.createShortSnackbar(message)
                    updateContentOfCurrentFragment()
                }
            }
        }
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }

    private fun updateContentOfCurrentFragment() {
//        val fragment = supportFragmentManager.findFragmentById(R.id.container) as FilesListFragment
//        fragment.updateDate()
        val broadcastIntent = Intent()
        broadcastIntent.action = applicationContext.getString(R.string.file_change_broadcast)
        broadcastIntent.putExtra(FileChangeBroadcastReceiver.EXTRA_PATH, backStackManager.top.path)
        sendBroadcast(broadcastIntent)
    }

}
