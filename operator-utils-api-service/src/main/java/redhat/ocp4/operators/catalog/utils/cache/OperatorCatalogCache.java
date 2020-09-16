package redhat.ocp4.operators.catalog.utils.cache;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import redhat.ocp4.operators.catalog.utils.GtarUtil;
import redhat.ocp4.operators.catalog.utils.dto.OperatorDetails;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperatorCatalogCache {
	private static final Logger logger = LoggerFactory.getLogger(OperatorCatalogCache.class);

	@Autowired
	private HazelcastInstance hazelcastInstance;

	private static final String IMAGE_OPERATORS_CACHE = "images-to-operators";

	private static long OPERATOR_HUB_FILE_LMT = 0;

	@Value("${operatorhub.tar.gz.file:/opt/operatorhub.tar.gz}")
	private String operatorHubFilePath;

	public void populateCacheIfNecessary() {
		File operatorHubFile;
		String operatorHubFileMissingErrMsg = "Did not find operatorHub tar.gz file at " + operatorHubFilePath
				+ " This file must exist as a reference catalog tar.gz for fetching image/operator details";
		if (operatorHubFilePath != null && operatorHubFilePath.startsWith("classpath:")) {
			try {
				operatorHubFile = ResourceUtils.getFile(operatorHubFilePath);
			} catch (FileNotFoundException e) {
				logger.error(operatorHubFileMissingErrMsg);
				throw new RuntimeException(operatorHubFileMissingErrMsg);
			}
		}else if (!(operatorHubFile = new File(operatorHubFilePath)).exists()) {
			logger.error(operatorHubFileMissingErrMsg);
			throw new RuntimeException(operatorHubFileMissingErrMsg);
		}

		if (operatorHubFile.lastModified() > OperatorCatalogCache.OPERATOR_HUB_FILE_LMT) { 
			// file has been updated, or we're seeing it for the first time
			try {
				logger.info("found new " + operatorHubFilePath + " file with LMT "
						+ new Date(operatorHubFile.lastModified()) + ". Populating cache");
				InputStream in = new FileInputStream(operatorHubFile);

				Map<String, List<OperatorDetails>> operatorDetailsList = GtarUtil.imageOperatorInfo(in);
				IMap<String, List<OperatorDetails>> operatorCache = hazelcastInstance.getMap(IMAGE_OPERATORS_CACHE);
				logger.debug("Clearing existing caches...");
				operatorCache.clear();
				logger.debug("Caches cleared!");
				operatorCache.putAll(operatorDetailsList);
				OperatorCatalogCache.OPERATOR_HUB_FILE_LMT = operatorHubFile.lastModified();
				logger.info("Successfully populated cache with " + operatorCache.size() + " operators!");
			} catch (IOException e) {
				String errMsg = "IOException caught loading the Operathub Cache from file " + operatorHubFilePath + " : " + e.getMessage();
				throw new RuntimeException(errMsg);
			}
		}

	}

	public List<OperatorDetails> getOperatorsForImage(String imageName) {
		IMap<String, List<OperatorDetails>> imagesToOperatorsCache = hazelcastInstance.getMap(IMAGE_OPERATORS_CACHE);
		List<OperatorDetails> result = imagesToOperatorsCache.get(imageName);
		return result == null ? new ArrayList<OperatorDetails>() : result;
	}

	public Map<String, List<OperatorDetails>> getOperatorsForImages(List<String> imageNames) {
		populateCacheIfNecessary();
		HashMap<String, List<OperatorDetails>> imagesToOperatorsMap = new HashMap<>();
		for (String imageName : imageNames) {
			imagesToOperatorsMap.put(imageName, getOperatorsForImage(imageName));
		}
		return imagesToOperatorsMap;
	}

	public Map<String, List<Map<String, String>>> getAllImagesToOperatorMappings() {
		return hazelcastInstance.getMap(IMAGE_OPERATORS_CACHE);
	}

}
