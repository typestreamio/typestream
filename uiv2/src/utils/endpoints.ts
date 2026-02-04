export function getGrpcHost(): string {
  const { hostname } = window.location;
  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return 'localhost:8080';
  }
  return `${hostname}:8080`;
}

export function getWeaviateBaseUrl(): string {
  const { hostname, protocol } = window.location;
  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return 'http://localhost:8090';
  }
  return `${protocol}//${hostname}/weaviate`;
}
