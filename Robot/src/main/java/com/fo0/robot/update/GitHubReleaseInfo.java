package com.fo0.robot.update;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubReleaseInfo {

	private boolean available;
	private String version; 
	private String message;
	
}
