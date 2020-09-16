package redhat.ocp4.operators.catalog.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * A Java Bean for transformation of Yaml ImagetContentSourcePolicy documents
 * @author lshulman
 *
 */
public class ImageContentSourcePolicyYaml {

	public static class Mirror {
		String[] mirrors = new String[] {};
		String source;
		public String[] getMirrors() {
			return mirrors;
		}
		public void setMirrors(String[] mirrors) {
			this.mirrors = mirrors;
		}
		public String getSource() {
			return source;
		}
		public void setSource(String source) {
			this.source = source;
		}
	}
	public class Spec {
		
		public Mirror[] getRepositoryDigestMirrors() {
			return repositoryDigestMirrors;
		}

		public void setRepositoryDigestMirrors(Mirror[] repositoryDigestMirrors) {
			this.repositoryDigestMirrors = repositoryDigestMirrors;
		}

		Mirror[]  repositoryDigestMirrors = new Mirror[] {};
	}
	String apiVersion = "operator.openshift.io/v1alpha1";
	String kind = "ImageContentSourcePolicy";
	Map<String, Object> metadata = new HashMap<String,Object>();
	Spec spec = new Spec();
	
	public String getApiVersion() {
		return apiVersion;
	}
	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}
	public String getKind() {
		return kind;
	}
	public void setKind(String kind) {
		this.kind = kind;
	}
	public Map<String, Object> getMetadata() {
		return metadata;
	}
	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
	public Spec getSpec() {
		return spec;
	}
	public void setSpec(Spec spec) {
		this.spec = spec;
	}
	
	
}
