import type { Product } from "../api/products";
import { formatVnd } from "../orders/format";

function ProductCard({ product }: { product: Product }) {
  return (
    <article className="product">
      <img src={product.image} alt={product.name} loading="lazy" />
      <div className="product-body">
        <b>{product.name}</b>
        <span className="num">{formatVnd(product.priceVnd)}</span>
        <small className={product.stock === 0 ? "out" : undefined}>
          {product.stock === 0 ? "Out of stock" : `${product.stock} in stock`} · best within {product.shelfLifeDays} days
        </small>
      </div>
    </article>
  );
}

export function ProductCards({ products }: { products: Product[] }) {
  return (
    <div className="products">
      {products.map((product) => (
        <ProductCard key={product.sku} product={product} />
      ))}
    </div>
  );
}
