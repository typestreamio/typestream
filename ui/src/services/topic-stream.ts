import { fileSystemClient, interactiveSessionClient } from './grpc-client';

export interface TopicStreamOptions {
  inputTopic: string;
  outputTopic: string;
}

/**
 * Stream data from one Kafka topic to another
 * @param options The input and output topic names
 * @returns A promise that resolves to a success message
 */
export async function streamBetweenTopics({ inputTopic, outputTopic }: TopicStreamOptions): Promise<string> {
  // First verify input topic exists
  const topics = await fileSystemClient.Ls({
    userId: 'local',
    path: '/dev/kafka/local/topics'
  });

  if (topics.error) {
    throw new Error(`Failed to list topics: ${topics.error}`);
  }

  if (!topics.files.includes(inputTopic)) {
    throw new Error(`Input topic "${inputTopic}" does not exist`);
  }

  // Start a session
  const sessionResponse = await interactiveSessionClient.StartSession({
    userId: 'local'
  });

  // Run the program to stream between topics
  const runResponse = await interactiveSessionClient.RunProgram({
    sessionId: sessionResponse.sessionId,
    source: `cat /dev/kafka/local/topics/${inputTopic} > /dev/kafka/local/topics/${outputTopic}`
  });

  if (runResponse.stdErr) {
    throw new Error(runResponse.stdErr);
  }

  return runResponse.stdOut || `Successfully started streaming from ${inputTopic} to ${outputTopic}`;
}
