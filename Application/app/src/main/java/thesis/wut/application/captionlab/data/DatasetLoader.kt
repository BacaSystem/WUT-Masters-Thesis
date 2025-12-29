package thesis.wut.application.captionlab.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.min

class DatasetLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "DatasetLoader"
        private const val DATASETS_DIR = "datasets"
    }
    
    private fun getDatasetsDirectory(): File {
        return context.getExternalFilesDir(DATASETS_DIR)
            ?: File(context.filesDir, DATASETS_DIR)
    }
    
    suspend fun loadCocoDataset(
        version: String,
        maxImages: Int = 100,
        startIndex: Int = 0,
        endIndex: Int = -1
    ): List<Pair<Bitmap, String>> = withContext(Dispatchers.IO) {
        val datasetDir = File(getDatasetsDirectory(), "coco_$version")
        val imagesDir = File(datasetDir, "images")
        val annotationsFile = File(datasetDir, "annotations/captions_$version.json")
        
        if (!datasetDir.exists() || !imagesDir.exists()) {
            Log.e(TAG, "COCO dataset not found at: ${datasetDir.absolutePath}")
            throw IllegalStateException(
                "COCO dataset not found. Please download and place in:\n" +
                "${datasetDir.absolutePath}\n\n" +
                "Directory structure:\n" +
                "coco_$version/\n" +
                "  images/\n" +
                "    COCO_${version}_*.jpg\n" +
                "  annotations/\n" +
                "    captions_$version.json"
            )
        }
        
        Log.i(TAG, "Loading COCO $version dataset from: ${datasetDir.absolutePath}")
        
        // List image files
        val allImageFiles = imagesDir.listFiles { file ->
            file.isFile && (file.extension == "jpg" || file.extension == "jpeg")
        }?.sortedBy { it.name } ?: emptyList()
        
        if (allImageFiles.isEmpty()) {
            throw IllegalStateException("No images found in: ${imagesDir.absolutePath}")
        }
        
        // Calculate actual end index
        val actualEndIndex = if (endIndex < 0) {
            min(startIndex + maxImages, allImageFiles.size)
        } else {
            min(endIndex, allImageFiles.size)
        }
        
        // Validate startIndex
        val validStartIndex = startIndex.coerceIn(0, allImageFiles.size - 1)
        
        // Extract subset
        val imageFiles = allImageFiles.subList(validStartIndex, actualEndIndex)
        
        Log.i(TAG, "Found ${allImageFiles.size} total image files, loading ${imageFiles.size} images (from $validStartIndex to $actualEndIndex)")
        
        // Load annotations if available
        val annotations = if (annotationsFile.exists()) {
            loadCocoAnnotations(annotationsFile)
        } else {
            Log.w(TAG, "Annotations file not found: ${annotationsFile.absolutePath}")
            emptyMap()
        }
        
        // Load images
        val loadedImages = mutableListOf<Pair<Bitmap, String>>()
        imageFiles.forEachIndexed { index, file ->
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val imageId = file.nameWithoutExtension
                    loadedImages.add(Pair(bitmap, imageId))
                    
                    if ((index + 1) % 10 == 0) {
                        Log.d(TAG, "Loaded ${index + 1}/${imageFiles.size} images (global index: ${validStartIndex + index + 1}/${allImageFiles.size})")
                    }
                } else {
                    Log.w(TAG, "Failed to decode image: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image ${file.name}: ${e.message}", e)
            }
        }
        
        Log.i(TAG, "Successfully loaded ${loadedImages.size} images from COCO $version (indices: $validStartIndex-$actualEndIndex)")
        loadedImages
    }
    
    private fun loadCocoAnnotations(annotationsFile: File): Map<String, List<String>> {
        return try {
            val json = JSONObject(annotationsFile.readText())
            val annotations = json.getJSONArray("annotations")
            
            val annotationsMap = mutableMapOf<String, MutableList<String>>()
            
            for (i in 0 until annotations.length()) {
                val annotation = annotations.getJSONObject(i)
                val imageId = annotation.getLong("image_id").toString()
                val caption = annotation.getString("caption")
                
                annotationsMap.getOrPut(imageId) { mutableListOf() }.add(caption)
            }
            
            Log.i(TAG, "Loaded annotations for ${annotationsMap.size} images")
            annotationsMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading annotations: ${e.message}", e)
            emptyMap()
        }
    }
    
    suspend fun loadFlickr30kDataset(
        maxImages: Int = 100,
        startIndex: Int = 0,
        endIndex: Int = -1
    ): List<Pair<Bitmap, String>> = withContext(Dispatchers.IO) {
        val datasetDir = File(getDatasetsDirectory(), "flickr30k")
        val imagesDir = File(datasetDir, "images")
        
        if (!datasetDir.exists() || !imagesDir.exists()) {
            Log.e(TAG, "Flickr30k dataset not found at: ${datasetDir.absolutePath}")
            throw IllegalStateException(
                "Flickr30k dataset not found. Please download and place in:\n" +
                "${datasetDir.absolutePath}\n\n" +
                "Directory structure:\n" +
                "flickr30k/\n" +
                "  images/\n" +
                "    *.jpg\n" +
                "  annotations/\n" +
                "    results_20130124.token"
            )
        }
        
        Log.i(TAG, "Loading Flickr30k dataset from: ${datasetDir.absolutePath}")
        
        // List image files
        val allImageFiles = imagesDir.listFiles { file ->
            file.isFile && (file.extension == "jpg" || file.extension == "jpeg")
        }?.sortedBy { it.name } ?: emptyList()
        
        if (allImageFiles.isEmpty()) {
            throw IllegalStateException("No images found in: ${imagesDir.absolutePath}")
        }
        
        // Calculate actual end index
        val actualEndIndex = if (endIndex < 0) {
            min(startIndex + maxImages, allImageFiles.size)
        } else {
            min(endIndex, allImageFiles.size)
        }
        
        // Validate startIndex
        val validStartIndex = startIndex.coerceIn(0, allImageFiles.size - 1)
        
        // Extract subset
        val imageFiles = allImageFiles.subList(validStartIndex, actualEndIndex)
        
        Log.i(TAG, "Found ${allImageFiles.size} total image files, loading ${imageFiles.size} images (from $validStartIndex to $actualEndIndex)")
        
        // Load images
        val loadedImages = mutableListOf<Pair<Bitmap, String>>()
        imageFiles.forEachIndexed { index, file ->
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val imageId = file.nameWithoutExtension
                    loadedImages.add(Pair(bitmap, imageId))
                    
                    if ((index + 1) % 10 == 0) {
                        Log.d(TAG, "Loaded ${index + 1}/${imageFiles.size} images (global index: ${validStartIndex + index + 1}/${allImageFiles.size})")
                    }
                } else {
                    Log.w(TAG, "Failed to decode image: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image ${file.name}: ${e.message}", e)
            }
        }
        
        Log.i(TAG, "Successfully loaded ${loadedImages.size} images from Flickr30k (indices: $validStartIndex-$actualEndIndex)")
        loadedImages
    }
    
    suspend fun loadCustomDataset(
        directoryName: String,
        maxImages: Int = 100,
        startIndex: Int = 0,
        endIndex: Int = -1
    ): List<Pair<Bitmap, String>> = withContext(Dispatchers.IO) {
        val imagesDir = File(getDatasetsDirectory(), directoryName)
        
        if (!imagesDir.exists() || !imagesDir.isDirectory) {
            Log.e(TAG, "Custom dataset directory not found: ${imagesDir.absolutePath}")
            throw IllegalStateException(
                "Custom dataset not found. Please create directory and add images:\n" +
                "${imagesDir.absolutePath}"
            )
        }
        
        Log.i(TAG, "Loading custom dataset from: ${imagesDir.absolutePath}")
        
        // List image files
        val allImageFiles = imagesDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "bmp")
        }?.sortedBy { it.name } ?: emptyList()
        
        if (allImageFiles.isEmpty()) {
            throw IllegalStateException("No images found in: ${imagesDir.absolutePath}")
        }
        
        // Calculate actual end index
        val actualEndIndex = if (endIndex < 0) {
            min(startIndex + maxImages, allImageFiles.size)
        } else {
            min(endIndex, allImageFiles.size)
        }
        
        // Validate startIndex
        val validStartIndex = startIndex.coerceIn(0, allImageFiles.size - 1)
        
        // Extract subset
        val imageFiles = allImageFiles.subList(validStartIndex, actualEndIndex)
        
        Log.i(TAG, "Found ${allImageFiles.size} total image files, loading ${imageFiles.size} images (from $validStartIndex to $actualEndIndex)")
        
        // Load images
        val loadedImages = mutableListOf<Pair<Bitmap, String>>()
        imageFiles.forEachIndexed { index, file ->
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val imageId = file.nameWithoutExtension
                    loadedImages.add(Pair(bitmap, imageId))
                    
                    if ((index + 1) % 10 == 0) {
                        Log.d(TAG, "Loaded ${index + 1}/${imageFiles.size} images (global index: ${validStartIndex + index + 1}/${allImageFiles.size})")
                    }
                } else {
                    Log.w(TAG, "Failed to decode image: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image ${file.name}: ${e.message}", e)
            }
        }
        
        Log.i(TAG, "Successfully loaded ${loadedImages.size} images from custom dataset (indices: $validStartIndex-$actualEndIndex)")
        loadedImages
    }
    
    fun datasetExists(type: String): Boolean {
        val datasetDir = File(getDatasetsDirectory(), type)
        return datasetDir.exists() && datasetDir.isDirectory
    }
    
    suspend fun getDatasetInfo(type: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val datasetDir = File(getDatasetsDirectory(), type)
        val imagesDir = File(datasetDir, "images")
        
        if (!imagesDir.exists()) {
            return@withContext mapOf(
                "exists" to false,
                "path" to datasetDir.absolutePath
            )
        }
        
        val imageFiles = imagesDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "bmp")
        } ?: emptyArray()
        
        mapOf(
            "exists" to true,
            "path" to datasetDir.absolutePath,
            "imageCount" to imageFiles.size,
            "totalSize" to imageFiles.sumOf { it.length() }
        )
    }
    
    fun getDownloadInstructions(): String {
        val datasetsDir = getDatasetsDirectory()
        
        return """
            Dataset Download Instructions
            ==============================
            
            Datasets should be placed in:
            ${datasetsDir.absolutePath}
            
            COCO Validation 2014:
            ---------------------
            1. Download from: https://cocodataset.org/#download
               - 2014 Val images [6K/1GB]
               - 2014 Train/Val annotations [241MB]
            
            2. Extract to:
               ${datasetsDir.absolutePath}/coco_val2014/
                 images/
                   COCO_val2014_000000000001.jpg
                   ...
                 annotations/
                   captions_val2014.json
            
            COCO Validation 2017:
            ---------------------
            1. Download from: https://cocodataset.org/#download
               - 2017 Val images [1GB]
               - 2017 Train/Val annotations [241MB]
            
            2. Extract to:
               ${datasetsDir.absolutePath}/coco_val2017/
                 images/
                   000000000001.jpg
                   ...
                 annotations/
                   captions_val2017.json
            
            Flickr30k:
            ----------
            1. Download from: https://www.kaggle.com/datasets/hsankesara/flickr-image-dataset
            
            2. Extract to:
               ${datasetsDir.absolutePath}/flickr30k/
                 images/
                   1000092795.jpg
                   ...
                 annotations/
                   results_20130124.token
            
            Custom Dataset:
            ---------------
            Create a folder and add images:
            ${datasetsDir.absolutePath}/my_custom_dataset/
              image001.jpg
              image002.jpg
              ...
            
            Using ADB:
            ----------
            # Push dataset to device
            adb push coco_val2014/ /sdcard/Android/data/thesis.wut.application.captionlab/files/datasets/coco_val2014/
            
            # Or push individual files
            adb push COCO_val2014_000000000001.jpg /sdcard/Android/data/thesis.wut.application.captionlab/files/datasets/coco_val2014/images/
        """.trimIndent()
    }
}
