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

	cmd := exec.Command("git", "tag", "--list", currentDate+"*")
	output, err := cmd.Output()
	if err != nil {
		fmt.Println("Error checking existing tags:", err)
		os.Exit(1)
	}

	existingTags := strings.Split(strings.TrimSuffix(string(output), "\n"), "\n")

	highestMicro := -1
	//TODO we only need to check the last tag
	for _, tag := range existingTags {
		parts := strings.Split(tag, "+")
		if len(parts) == 2 {
			micro := parts[1]
			microInt := 0
			_, err := fmt.Sscanf(micro, "%d", &microInt)
			if err == nil && microInt > highestMicro {
				highestMicro = microInt
			}
		}
	}

	if highestMicro == -1 {
		highestMicro = 0
	} else {
		highestMicro++
	}

	return fmt.Sprintf("%s+%d", currentDate, highestMicro)
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
	dockerVersion := strings.Replace(version, "+", ".", -1)
	cmd := exec.Command("./gradlew", "-Pversion="+dockerVersion, ":server:jibDockerBuild")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("Error building server:", err)
		os.Exit(1)
	}

	// Push the Docker image
	pushCmd := exec.Command("docker", "push", "typestream/server:"+dockerVersion)
	pushCmd.Stdout = os.Stdout
	pushCmd.Stderr = os.Stderr
	if err := pushCmd.Run(); err != nil {
		fmt.Println("Error pushing Docker image:", err)
		os.Exit(1)
	}
}

func buildSeederImage(version string) {
	dockerVersion := strings.Replace(version, "+", ".", -1)
	cmd := exec.Command("./gradlew", "-Pversion="+dockerVersion, ":tools:jibDockerBuild")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("Error building tools:", err)
		os.Exit(1)
	}

	// Push the Docker image
	pushCmd := exec.Command("docker", "push", "typestream/tools-seeder:"+dockerVersion)
	pushCmd.Stdout = os.Stdout
	pushCmd.Stderr = os.Stderr
	if err := pushCmd.Run(); err != nil {
		fmt.Println("Error pushing Docker image:", err)
		os.Exit(1)
	}

}

func releaseClient(version string) {
	cliPath := "./cli"

	cmd := exec.Command("goreleaser", "--clean")
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
