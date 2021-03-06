package redhat.ocp4.operators.catalog.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;
import org.apache.commons.compress.archivers.ArchiveEntry;

import com.fasterxml.jackson.databind.ObjectMapper;

import redhat.ocp4.operators.catalog.utils.dto.OperatorDetails;

import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
@SpringBootTest
class OperatorCatalogUtilApplicationTests {

	@Value("classpath:operatorhub-manifests-5-31-20.tar.gz")
	Resource operatorHubArchive;

	@Value("classpath:operatorhub-mapping-5-31-20.txt")
	Resource operatorHubMappingFile;

	@Value("classpath:operatorHubImageContentSourcePoicy.yaml")
	Resource operatorHubICSPYaml;

	
	
	@Test
	void testExtractOperatorDetailsFromArchiveEntryPath() {
		//some happy path tests to extract operator name and version from file paths that are legit
		ArchiveEntry entry = mock(ArchiveEntry.class);
		when(entry.getName()).thenReturn("manifests/manifests-123XXX/operator-name/operator-dir-xxx/v1.0.0/op-name.v1.0.0.clusterserviceversion.yml");
		
		OperatorDetails details = GtarUtil.extractOperatorDetailsFromEntry(entry);
		assertEquals(details, OperatorDetails.builder().operatorName("operator-name").version("v1.0.0").build());
		
		//there are cases where there is no intermediate operator-dir-xxx directory before the version
		when(entry.getName()).thenReturn("manifests/manifests-123XXX/operator-name/v1.0.0/op-name.v1.0.0.clusterserviceversion.yml");
		details = GtarUtil.extractOperatorDetailsFromEntry(entry);
		assertEquals(details, OperatorDetails.builder().operatorName("operator-name").version("v1.0.0").build());

		//and there are cases where there is no directory specifically for the version. Overall
		//the version should be extracted from the filename itself, not the dir name
		when(entry.getName()).thenReturn("manifests/manifests-123XXX/operator-name/operator-dir-xxx/op-name.v1.0.0.clusterserviceversion.yml");
		details = GtarUtil.extractOperatorDetailsFromEntry(entry);
		assertEquals(details, OperatorDetails.builder().operatorName("operator-name").version("v1.0.0").build());

		//edge cases:
		
		//if the path of directories is not at all what we should expect, we don't want to throw exception
		//we wan't to return a "dummy" OperatorDetails with 'N/A' as values:
		when(entry.getName()).thenReturn("manifests/op-name.v1.0.0.clusterserviceversion.yml");
		details = GtarUtil.extractOperatorDetailsFromEntry(entry);
		assertEquals(details, OperatorDetails.builder().operatorName("N/A").version("N/A").build());

		//it may be that just the version is not set correctly in the file path
		//similarly, we still want to return an OperatorDetails successfully, but with 'N/A' as the version
		when(entry.getName()).thenReturn("manifests/manifests-123XXX/operator-name/operator-dir-xxx/clusterserviceversion.yml");
		details = GtarUtil.extractOperatorDetailsFromEntry(entry);
		assertEquals(details, OperatorDetails.builder().operatorName("operator-name").version("N/A").build());
		
		//we know that codeready workspaces, and only codeready workspaces operator, doesn't name its CSV files *.clusterserviceversion.*ml
		//we accomodate this special case
		when(entry.getName()).thenReturn("manifests/manifests-024123111/codeready-workspaces/codeready-workspaces-j5r6u9jw/v2.2.0/codeready-workspaces.csv.yaml");
		details = GtarUtil.extractOperatorDetailsFromEntry(entry);
		assertEquals(details, OperatorDetails.builder().operatorName("codeready-workspaces").version("v2.2.0").build());
	}
	
	@Test
	void testImageOperatorInfo() {
		Map<String, List<OperatorDetails>> details = null;
		try {
			details = GtarUtil.imageOperatorInfo(operatorHubArchive.getInputStream());
		} catch (IOException e) {
			fail(e.getMessage());
		}

		//make sure at least the size of imagesInGtarArchive() is the same as our method's
		try {
			assertEquals(new TreeSet<String>(Arrays.asList(GtarUtil.imagesInGtarArchive(operatorHubArchive.getInputStream()))), new TreeSet<String>(details.keySet()));
		} catch (IOException e) {
			fail(e.getMessage());
		}
		// make sure that our legit operatorhub archive doesn't return any operator
		// details where the operatorName or Version are 'N/A'
		// the code should be able to extract the operator name and version from all of
		// the sources
		assertFalse(details.values().stream().flatMap(List::stream)
				.anyMatch(dets -> dets.getOperatorName() == null || dets.getOperatorName().equals("N/A")
						|| dets.getVersion() == null || dets.getVersion().equals("N/A")));
		

		//we know that some images belong to multiple versions of the same operator, or even to different operators alltogether
		//lets double check that our code found them properly
		List<String> oauth2Proxy = details.get("quay.io/pusher/oauth2_proxy:latest").stream().map(o -> o.getOperatorName()).distinct().collect(Collectors.toList());
		assertTrue(oauth2Proxy.containsAll(Arrays.asList("amq-online", "enmasse")));
		List<String> logging = details.get("registry.redhat.io/openshift4/ose-logging-elasticsearch5@sha256:e4cca1134b7213c8877ac6522fcad172e0f47eea799020eed25b90dc928af28e").stream().map(o -> o.getOperatorName()).distinct().collect(Collectors.toList());
		assertTrue(logging.containsAll(Arrays.asList("cluster-logging", "elasticsearch-operator")));
	}
	
	@Test
	void testOperatorInfoForImage() {

		// portworx is a rare case where the operatorname in the file is not the same as
		// the operator id
		String image = "registry.connect.redhat.com/portworx/openstorage-operator:1.0.4";
		try {
			assertEquals(
					Arrays.asList(new OperatorDetails[] {
							OperatorDetails.builder().operatorName("portworx-certified").version("v1.0.4").build() }),
					GtarUtil.operatorInfoFromImage(image, operatorHubArchive.getInputStream()));
		} catch (IOException e) {
			fail(e.getMessage());
		}

		// example of image that appears in two different versions of the operator
		image = "quay.io/screeley44/aws-s3-provisioner:v1.0.0";
		try {
			assertEquals(
					Arrays.asList(new OperatorDetails[] {
							OperatorDetails.builder().operatorName("awss3-operator-registry").version("1.0.0").build(),
							OperatorDetails.builder().operatorName("awss3-operator-registry").version("1.0.1")
									.build() }),
					GtarUtil.operatorInfoFromImage(image, operatorHubArchive.getInputStream()));
		} catch (IOException e) {
			fail(e.getMessage());
		}

		//image is not in catalog, so should return null
		image = "junk";
		try {
			assertEquals(GtarUtil.operatorInfoFromImage(image, operatorHubArchive.getInputStream()), new ArrayList<OperatorDetails>());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	void testPruneCatalog() {
		TarArchiveInputStream tis;
		try {
			tis = new TarArchiveInputStream(new GzipCompressorInputStream(operatorHubArchive.getInputStream()));

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			TarArchiveOutputStream tos = new TarArchiveOutputStream(new GzipCompressorOutputStream(bos));
			tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
			tos.setAddPaxHeadersForNonAsciiNames(true);

			Set<String> imageRepos = new TreeSet<String>(
					Arrays.asList("coreos/prometheus-operator", "couchbase/operator", "opstree/redis-operator"));
			List<String> allImages = Arrays.asList(GtarUtil.imagesInGtarArchive(operatorHubArchive.getInputStream()));
			//we assert that there are images from repos other than the three we're testing this above
			//after we prune the catalog, we'll run this test on our pruned catalog, and should get 0
			assertTrue(allImages.stream().filter(s -> !imageRepos.stream().anyMatch(repo -> s.contains(repo))).collect(Collectors.toList()).size() > 0);
			
			GtarUtil.pruneCatalog(imageRepos, tis, tos);
			
			List<String> imagesFromPrunedCatalog = Arrays.asList(GtarUtil.imagesInGtarArchive(new ByteArrayInputStream(bos.toByteArray())));
			
			assertEquals(imagesFromPrunedCatalog.stream().filter(s -> !imageRepos.stream().anyMatch(repo -> s.contains(repo))).collect(Collectors.toList()).size(), 0);
		} catch (IOException e) {
			e.printStackTrace();

			fail(e.getMessage());
		}

	}

	@Test
	void testListEntriesInGtarArchive() {
		assertTrue(operatorHubArchive.exists());
		try {
			String[] archiveEntries = GtarUtil.listEntriesInGtarArchive(operatorHubArchive.getInputStream());
			assertEquals(2958, archiveEntries.length);
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	void testOcImageMirrorInput() {
		try {
			String mappings = GtarUtil.createMirrorMappings(operatorHubArchive.getInputStream(), "test.io");
			StringBuffer buf = new StringBuffer();
			IOUtils.readLines(operatorHubMappingFile.getInputStream()).stream().distinct().sorted()
					.forEach(s -> buf.append(s).append(System.lineSeparator()));
			// the output of the api method should not equal what "oc adm catalog mirror"
			// produces, because "oc adm catalog mirror"
			// actually misses some images that need to be mirrors. We test here to ensure
			// that the api at least
			// includes all of the images that "oc adm catalog mirror" produces
			assertNotEquals(buf.toString(), mappings);
			TreeSet<String> ocAdmCatalogMirrorLines = new TreeSet<String>(
					IOUtils.readLines(new StringReader(buf.toString())));
			TreeSet<String> apiMirrorLines = new TreeSet<String>(IOUtils.readLines(new StringReader(mappings)));
			assertTrue(apiMirrorLines.size() > ocAdmCatalogMirrorLines.size());
			// ensure that the api result contains everything that "oc adm catalog mirror"
			// produces
			assertTrue(apiMirrorLines.containsAll(ocAdmCatalogMirrorLines));
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	void testApplyImageMirrors() {
		try {
			// get list of image registries in our input tar.gz.
			List<String> imageRegistries = GtarUtil.registriesinGtarArchive(operatorHubArchive.getInputStream());
			assertEquals(new TreeSet<Object>(imageRegistries),
					new TreeSet<Object>(Arrays.asList(new String[] { "docker.io", "gcr.io", "k8s.gcr.io",
							"lightbend-docker-registry.bintray.io", "quay.io", "registry.access.redhat.com",
							"registry.connect.redhat.com", "registry.redhat.io", "registry.svc.ci.openshift.org" })));
			// we'll create a simple mirror map, which just prepends "mirror." for each
			// registry, so that "docker.io" -> "mirror.docker.io"
			Map<String, String> mirrors = imageRegistries.stream()
					.collect(Collectors.toMap(s -> s, s -> "mirror." + s));

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			GtarUtil.applyImageMirrors(mirrors,
					new TarArchiveInputStream(new GzipCompressorInputStream(operatorHubArchive.getInputStream())),
					new TarArchiveOutputStream(new GzipCompressorOutputStream(baos)));
			baos.flush();
			baos.close();
			// get list of registries from the result tar.gz. We expect them all to start
			// with "mirror."
			List<String> mirroredRegistries = GtarUtil
					.registriesinGtarArchive(new ByteArrayInputStream(baos.toByteArray()));
			assertEquals(imageRegistries.size(), mirroredRegistries.size());
			assertEquals(mirroredRegistries,
					imageRegistries.stream().map(s -> "mirror." + s).collect(Collectors.toList()));
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

}
