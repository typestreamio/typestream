package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"time"
)

func generateVersion() string {
	currentDate := time.Now().Format("2006.01.02")

	cmd := exec.Command("git", "tag", "--list", currentDate+".*")
	output, err := cmd.Output()
	if err != nil {
		fmt.Println("Error checking existing tags:", err)
		os.Exit(1)
	}

	existingTags := strings.Split(string(output), "\n")
	highestMicro := 0
	for _, tag := range existingTags {
		parts := strings.Split(tag, ".")
		if len(parts) == 4 {
			micro := parts[3]
			microInt := 0
			_, err := fmt.Sscanf(micro, "%d", &microInt)
			if err == nil && microInt > highestMicro {
				highestMicro = microInt
			}
		}
	}

	return fmt.Sprintf("%s.%d", currentDate, highestMicro+1)
}

func updateChangelog(version string) {
}

func gitTag(version string) {
	cmd := exec.Command("git", "tag", version)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("Error tagging version:", err)
	}
}

func buildServerImage(version string) {
	cmd := exec.Command("./gradlew", "-Pversion="+version, ":server:jibDockerBuild")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("Error building server:", err)
		os.Exit(1)
	}

	// Push the Docker image
	pushCmd := exec.Command("docker", "push", "typestream/server:"+version)
	pushCmd.Stdout = os.Stdout
	pushCmd.Stderr = os.Stderr
	if err := pushCmd.Run(); err != nil {
		fmt.Println("Error pushing Docker image:", err)
		os.Exit(1)
	}
}

func buildSeederImage(version string) {
	cmd := exec.Command("./gradlew", "-Pversion="+version, ":tools:jibDockerBuild")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("Error building tools:", err)
		os.Exit(1)
	}

	// Push the Docker image
	pushCmd := exec.Command("docker", "push", "typestream/tools-seeder:"+version)
	pushCmd.Stdout = os.Stdout
	pushCmd.Stderr = os.Stderr
	if err := pushCmd.Run(); err != nil {
		fmt.Println("Error pushing Docker image:", err)
		os.Exit(1)
	}

}

func releaseClient(version string) {
	cliPath := "./cli"

	cmd := exec.Command("goreleaser")
	cmd.Dir = cliPath
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("Error executing goreleaser:", err)
		os.Exit(1)
	}
}

func generateGitHubRelease(version string) {
}

func announceOnDiscord(version string) {
}

func main() {
	version := generateVersion()
	fmt.Printf("New version: %s\n", version)

	updateChangelog(version)
	gitTag(version)

	buildServerImage(version)
	buildSeederImage(version)
	releaseClient(version)

	generateGitHubRelease(version)
	announceOnDiscord(version)
}
