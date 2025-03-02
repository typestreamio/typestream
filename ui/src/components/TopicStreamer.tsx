import React, { useState } from 'react';
import { useKafkaTopics } from '../services/kafka';
import { streamBetweenTopics } from '../services/topic-stream';

export function TopicStreamer() {
  const { data: topics = [], isLoading, error: topicsError } = useKafkaTopics();
  const [inputTopic, setInputTopic] = useState('');
  const [outputTopic, setOutputTopic] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleStream = async () => {
    try {
      setError(null);
      setMessage(null);
      
      if (!inputTopic) {
        throw new Error('Please select an input topic');
      }
      if (!outputTopic) {
        throw new Error('Please enter a name for the new output topic');
      }

      setIsStreaming(true);
      const result = await streamBetweenTopics({
        inputTopic,
        outputTopic
      });

      setMessage(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setIsStreaming(false);
    }
  };

  return (
    <div style={{ marginTop: '40px', padding: '20px', borderTop: '1px solid #eee' }}>
      <h2>Stream Between Topics</h2>
      
      {isLoading ? (
        <div style={{ marginBottom: '20px' }}>Loading topics...</div>
      ) : topicsError ? (
        <div style={{ color: 'red', marginBottom: '20px' }}>
          Failed to load topics: {topicsError instanceof Error ? topicsError.message : 'Unknown error'}
        </div>
      ) : topics.length === 0 ? (
        <div style={{ marginBottom: '20px' }}>No topics available</div>
      ) : (
        <div style={{ marginBottom: '20px' }}>
          <div style={{ marginBottom: '10px' }}>
            <label style={{ display: 'block', marginBottom: '5px' }}>Input Topic:</label>
            <select 
              value={inputTopic} 
              onChange={(e) => setInputTopic(e.target.value)}
              style={{ width: '200px', padding: '5px' }}
              disabled={isStreaming}
            >
              <option value="">Select a topic</option>
              {topics.map(topic => (
                <option key={topic} value={topic}>{topic}</option>
              ))}
            </select>
          </div>

          <div style={{ marginBottom: '10px' }}>
            <label style={{ display: 'block', marginBottom: '5px' }}>New Output Topic:</label>
            <input 
              type="text"
              value={outputTopic} 
              onChange={(e) => setOutputTopic(e.target.value)}
              placeholder="Enter new topic name"
              style={{ 
                width: '200px', 
                padding: '5px',
                border: '1px solid #ccc',
                borderRadius: '4px',
                fontSize: '14px'
              }}
              disabled={isStreaming}
            />
          </div>

          <button 
            onClick={handleStream}
            disabled={isStreaming}
            style={{ 
              padding: '8px 16px',
              backgroundColor: isStreaming ? '#6c757d' : '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: isStreaming ? 'not-allowed' : 'pointer'
            }}
          >
            {isStreaming ? 'Starting Stream...' : 'Start Streaming'}
          </button>
        </div>
      )}

      {error && (
        <div style={{ 
          color: '#721c24',
          backgroundColor: '#f8d7da',
          padding: '15px',
          marginBottom: '20px',
          borderRadius: '4px'
        }}>
          {error}
        </div>
      )}

      {message && (
        <div style={{ 
          marginTop: '20px',
          padding: '15px',
          backgroundColor: '#d4edda',
          color: '#155724',
          borderRadius: '4px'
        }}>
          {message}
        </div>
      )}
    </div>
  );
}
