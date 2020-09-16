package redhat.ocp4.operators.catalog.utils.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Builder
@EqualsAndHashCode
@ToString
public class OperatorDetails implements Serializable {

	private final String operatorName;
	private final String version;
}
