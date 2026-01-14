import { useQuery } from '@connectrpc/connect-query';
import { StateQueryService } from '../generated/state_query_connect';

export function useListStores() {
  return useQuery(
    { ...StateQueryService.methods.listStores, service: StateQueryService },
    {}
  );
}
