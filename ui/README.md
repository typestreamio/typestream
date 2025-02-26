# TypeStream UI

The TypeStream UI is a React-based web interface for interacting with TypeStream services.

## Development

1. Install dependencies:
```bash
npm install
```

2. Generate gRPC types:
```bash
npm run proto
```

3. Start the development server:
```bash
npm run dev
```

The UI will be available at http://localhost:5173 (or next available port).

Note: Make sure the TypeStream CLI is running (`typestream dev`) before starting the UI.

## Testing

The UI includes both unit tests and integration tests.

### Unit Tests

Run all unit tests:
```bash
npm test
```

This will run all tests and watch for changes.

### Integration Tests

Integration tests require the TypeStream services to be running (`typestream dev`). These tests verify the interaction with real services.

Run integration tests only:
```bash
npm test -- KafkaTopics.integration.test.tsx
```

### Test Files

- `*.test.tsx` - Unit tests that mock external dependencies
- `*.integration.test.tsx` - Integration tests that use real services
