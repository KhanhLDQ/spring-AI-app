import { request } from "./http";

export interface Product {
  sku: string;
  name: string;
  priceVnd: number;
  stock: number;
  shelfLifeDays: number;
  description: string;
  image: string;
}

export const fetchProducts = (token: string) => request<Product[]>(token, "/api/products");
