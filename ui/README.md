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
