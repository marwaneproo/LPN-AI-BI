import type {
  BiAchatsFilters,
  BiAchatsResponse,
  BiArticlesFilters,
  BiArticlesResponse,
  BiClientsFilters,
  BiClientsResponse,
  BiOrdersFilters,
  BiOrdersResponse,
  BiOverviewFilters,
  BiOverviewResponse,
  BiRevenueFilters,
  BiRevenueResponse,
} from "../types/bi.types";
import { API_BASE_URL, apiFetch } from "../../../api/client";

async function readApiResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    throw new Error(errorText ? `Backend ${response.status}: ${errorText}` : `Backend ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function fetchBiOverview(filters: BiOverviewFilters = {}): Promise<BiOverviewResponse> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.granularity) params.set("granularity", filters.granularity);
  if (filters.compare !== undefined) params.set("compare", String(filters.compare));
  const query = params.toString();
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/overview${query ? `?${query}` : ""}`);
  return readApiResponse<BiOverviewResponse>(response);
}

export async function fetchBiRevenue(filters: BiRevenueFilters = {}): Promise<BiRevenueResponse> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.granularity) params.set("granularity", filters.granularity);
  if (filters.compare !== undefined) params.set("compare", String(filters.compare));
  if (filters.commercial !== undefined) params.set("commercial", String(filters.commercial));
  const query = params.toString();
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/revenue${query ? `?${query}` : ""}`);
  return readApiResponse<BiRevenueResponse>(response);
}

export async function fetchBiOrders(filters: BiOrdersFilters = {}): Promise<BiOrdersResponse> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.granularity) params.set("granularity", filters.granularity);
  if (filters.compare !== undefined) params.set("compare", String(filters.compare));
  if (filters.commercial !== undefined) params.set("commercial", String(filters.commercial));
  const query = params.toString();
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/orders${query ? `?${query}` : ""}`);
  return readApiResponse<BiOrdersResponse>(response);
}

export async function fetchBiArticles(filters: BiArticlesFilters = {}): Promise<BiArticlesResponse> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.granularity) params.set("granularity", filters.granularity);
  if (filters.compare !== undefined) params.set("compare", String(filters.compare));
  const query = params.toString();
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/articles${query ? `?${query}` : ""}`);
  return readApiResponse<BiArticlesResponse>(response);
}

export async function fetchBiClients(filters: BiClientsFilters = {}): Promise<BiClientsResponse> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.granularity) params.set("granularity", filters.granularity);
  if (filters.compare !== undefined) params.set("compare", String(filters.compare));
  if (filters.commercial !== undefined) params.set("commercial", String(filters.commercial));
  if (filters.customer !== undefined) params.set("customer", String(filters.customer));
  if (filters.category !== undefined) params.set("category", String(filters.category));
  if (filters.supplier !== undefined) params.set("supplier", String(filters.supplier));
  if (filters.region) params.set("region", filters.region);
  if (filters.city) params.set("city", filters.city);
  if (filters.payment_status) params.set("payment_status", filters.payment_status);
  if (filters.limit !== undefined) params.set("limit", String(filters.limit));
  if (filters.offset !== undefined) params.set("offset", String(filters.offset));
  const query = params.toString();
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/clients${query ? `?${query}` : ""}`);
  return readApiResponse<BiClientsResponse>(response);
}

export async function fetchBiAchats(filters: BiAchatsFilters = {}): Promise<BiAchatsResponse> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.granularity) params.set("granularity", filters.granularity);
  if (filters.compare !== undefined) params.set("compare", String(filters.compare));
  if (filters.supplier !== undefined) params.set("supplier", String(filters.supplier));
  if (filters.category !== undefined) params.set("category", String(filters.category));
  const query = params.toString();
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/achats${query ? `?${query}` : ""}`);
  return readApiResponse<BiAchatsResponse>(response);
}
