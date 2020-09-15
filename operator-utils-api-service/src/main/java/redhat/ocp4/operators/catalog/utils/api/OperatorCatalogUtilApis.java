package redhat.ocp4.operators.catalog.utils.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.util.IOUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import redhat.ocp4.operators.catalog.utils.GtarUtil;
import redhat.ocp4.operators.catalog.utils.dto.OperatorDetails;

@Api(value = "Operator Catalog Management Apis")
@RestController
public class OperatorCatalogUtilApis {
	
	@Value("${operatorhub.tar.gz.file:/opt/operatorhub/operatorhub.tar.gz}")
	private String operatorHubFilePath;

	@ApiOperation(value = "produces a unique list of all of the images referenced in a tar.gz catalog manifests file")
	@PostMapping("/listAllImagesInCatalogManifests")
	public String[] listAllImages(@RequestParam("file") MultipartFile file) throws IOException {
		return GtarUtil.imagesInGtarArchive(file.getInputStream());
	}

	@ApiOperation(value = "returns a mapping.txt file from a tar.gz catalog  manifests file. The result is like that produced by a 'oc adm catalog mirror' command. See https://docs.openshift.com/container-platform/4.3/operators/olm-restricted-networks.html#olm-restricted-networks-operatorhub_olm-restricted-networks")
	@PostMapping("/mirrorImagesInCatalogManifests")
	public String mirrorImages(@RequestParam("file") MultipartFile file, @RequestParam("mirrorUrl") String mirror)
			throws IOException {
		return GtarUtil.createMirrorMappings(file.getInputStream(), mirror);
	}

	@ApiOperation(value = "creates an ImageContentSourcePolicy resource in Yaml from a tar.gz catalog manifests file. The result is exactly as produced by a 'oc adm catalog mirror' command. See https://docs.openshift.com/container-platform/4.3/operators/olm-restricted-networks.html#olm-restricted-networks-operatorhub_olm-restricted-networks")
	@PostMapping("/createImageContentSourcePolicy")
	public String imageContentSourcePolicy(@RequestParam("file") MultipartFile file,
			@RequestParam("mirrorUrl") String mirror, @RequestParam("name") String name) throws IOException {
		return GtarUtil.createImageContentSourcePolicy(file.getInputStream(), mirror, name);
	}

	@ApiOperation(value = "takes a tar.gz package manifest archive, and a map of registry mirrors to apply, and produces the same tar.gz archive, but with the mirror applied to every image reference ")
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@PostMapping("/applyImageMirrors")
	public void applyImageMirrors(@RequestParam("package-manifest-file") MultipartFile file, 
			@RequestParam("json-mirrors-file") MultipartFile mirrorsFile,
			HttpServletResponse response) throws IOException {
		Map mirrors = new ObjectMapper().readValue(mirrorsFile.getInputStream(), Map.class);
		GtarUtil.applyImageMirrors(mirrors,
				new TarArchiveInputStream(new GzipCompressorInputStream(file.getInputStream())),
				new TarArchiveOutputStream(new GzipCompressorOutputStream(response.getOutputStream())));
		response.flushBuffer();
	}
	
	@ApiOperation(value = "takes a tar.gz package manifest archive, and simple Json Array of Operator image repos (Ex: '[\n" + 
			"  \"coreos/prometheus-operator\",\n" + 
			"  \"couchbase/operator\",\n" + 
			"  \"opstree/redis-operator\"\n" + 
			"]'\n" + 
			", and returns a tar.gz package manifest archive by pruning the original to only contain Operators with images from those repos")
	@SuppressWarnings("unchecked")
	@PostMapping("/pruneCatalog")
	public void pruneCatalog(@RequestParam("package-manifest-file") MultipartFile file,
			@RequestParam("repositories-file") MultipartFile reposListFile, HttpServletResponse response) throws JsonParseException, JsonMappingException, IOException {
		Set<String> reposList = new TreeSet<String>(new ObjectMapper().readValue(reposListFile.getInputStream(), List.class));
		GtarUtil.pruneCatalog(reposList,
				new TarArchiveInputStream(new GzipCompressorInputStream(file.getInputStream())),
				new TarArchiveOutputStream(new GzipCompressorOutputStream(response.getOutputStream())));
		response.flushBuffer();
	}
	
	@ApiOperation(value = "produces a unique list of registry hostnames from all of the images in the tar.gz archive. Useful if you need to figure out which registries  you might need credentials for to apply mirrors")
	@PostMapping("/listRegistriesInCatalogManifests")
	public List<String> listRegistriesInCatalogManifests(@RequestParam("file") MultipartFile file) throws IOException{
		return GtarUtil.registriesinGtarArchive(file.getInputStream());
	}
	
	@ApiOperation(value = "returns a list of operators that an image belongs to, by referencing the tar.gz file sources that is passed")
	@PostMapping("/listOperatorDetails")
	public Map<String,List<OperatorDetails>> listImageOperatorDetails(@RequestParam(value="file") MultipartFile file) throws IOException {
		return GtarUtil.imageOperatorInfo(file.getInputStream());
	}
	
	@ApiOperation(value = "returns a list of operators that an image belongs to, by referencing the tar.gz file sources that is passed")
	@PostMapping("/images/operators")
	public List<String> listOperatorsForImageFromCatalog(@RequestParam("name") String imageName, @RequestParam(value="file") MultipartFile file) throws IOException {
		return GtarUtil.operatorInfoFromImage(imageName, file.getInputStream()).stream().map(details -> details.getOperatorName()).collect(Collectors.toList());
	}
	
	
	@SuppressWarnings("resource")
	@ApiOperation(value = "returns a list of operators that an image belongs to, by referencing an internal Operator Catalog tar.gz file. Will return a 500 if no internal Operator Catalog tar.gz file has been configured or mounted to this service")
	@GetMapping("/images/operators")
	public List<String> listOperatorsForImage(@RequestParam("name") String imageName) throws IOException {
		File operatorHubFile = new File(operatorHubFilePath);
		if (operatorHubFile.exists()) {
			return GtarUtil.operatorInfoFromImage(imageName, new FileInputStream(operatorHubFile)).stream().map(details -> details.getOperatorName()).collect(Collectors.toList());
		}else {
			String errMsg = "operatorhub tar.gz file does not exist at configured path " + operatorHubFilePath + " to serve GET '/images/operators/' request! ";
			Logger.getLogger(this.getClass().getName()).severe(errMsg);
			throw new RuntimeException(errMsg);
		}
	}
}
