# Examples

Complete, working examples for common SQL Connect use cases.

---

## Movie Review App

A complete schema for a movie database with reviews, actors, and user authentication.

### Schema

```graphql
# schema.gql

# Users
type User @table(key: "uid") {
  uid: String! @default(expr: "auth.uid")
  email: String! @unique
  displayName: String
  createdAt: Timestamp! @default(expr: "request.time")
}

# Movies
type Movie @table {
  id: UUID! @default(expr: "uuidV4()")
  title: String!
  releaseYear: Int
  genre: String @index
  rating: Float
  description: String
  posterUrl: String
  createdAt: Timestamp! @default(expr: "request.time")
}

# Movie metadata (one-to-one)
type MovieMetadata @table {
  movie: Movie! @unique
  director: String
  runtime: Int
  budget: Int64
}

# Actors
type Actor @table {
  id: UUID! @default(expr: "uuidV4()")
  name: String!
  birthDate: Date
}

# Movie-Actor relationship (many-to-many)
type MovieActor @table(key: ["movie", "actor"]) {
  movie: Movie!
  actor: Actor!
  role: String!  # "lead" or "supporting"
  character: String
}

# Reviews (user-owned)
type Review @table @unique(fields: ["movie", "user"]) {
  id: UUID! @default(expr: "uuidV4()")
  movie: Movie!
  user: User!
  rating: Int!
  text: String
  createdAt: Timestamp! @default(expr: "request.time")
}
```

### Queries

```graphql
# queries.gql

# Public: List movies with filtering
query ListMovies($genre: String, $minRating: Float, $limit: Int) 
  @auth(level: PUBLIC) {
  movies(
    where: {
      genre: { eq: $genre },
      rating: { ge: $minRating }
    },
    orderBy: [{ rating: DESC }],
    limit: $limit
  ) {
    id title genre rating releaseYear posterUrl
  }
}

# Public: Get movie with full details
query GetMovie($id: UUID!) @auth(level: PUBLIC) {
  movie(id: $id) {
    id title genre rating releaseYear description
    metadata: movieMetadata_on_movie { director runtime }
    actors: actors_via_MovieActor { name }
    reviews: reviews_on_movie(orderBy: [{ createdAt: DESC }], limit: 10) {
      rating text createdAt
      user { displayName }
    }
  }
}

# User: Get my reviews
query MyReviews @auth(level: USER) {
  reviews(where: { user: { uid: { eq_expr: "auth.uid" }}}) {
    id rating text createdAt
    movie { id title posterUrl }
  }
}
```

### Mutations

```graphql
# mutations.gql

# User: Create/update profile on first login
mutation UpsertUser($email: String!, $displayName: String) @auth(level: USER) {
  user_upsert(data: {
    uid_expr: "auth.uid",
    email: $email,
    displayName: $displayName
  })
}

# User: Add review (one per movie per user)
mutation AddReview($movieId: UUID!, $rating: Int!, $text: String) 
  @auth(level: USER) {
  review_upsert(data: {
    movie: { id: $movieId },
    user: { uid_expr: "auth.uid" },
    rating: $rating,
    text: $text
  })
}

# User: Delete my review
mutation DeleteReview($id: UUID!) @auth(level: USER) {
  review_delete(
    first: { where: {
      id: { eq: $id },
      user: { uid: { eq_expr: "auth.uid" }}
    }}
  )
}
```

### Realtime Queries

```graphql
# queries.gql (realtime additions)

# Auto-refresh: this single-entity lookup refreshes automatically
# when any mutation modifies this specific movie. No @refresh needed.
query GetMovie($id: UUID!) @auth(level: PUBLIC) {
  movie(id: $id) {
    id title genre rating releaseYear description
    metadata: movieMetadata_on_movie { director runtime }
    reviews: reviews_on_movie(orderBy: [{ createdAt: DESC }], limit: 10) {
      rating text createdAt
      user { displayName }
    }
  }
}

# Event-driven: Simple refresh when any movie is added
query ListMoviesSimple @auth(level: PUBLIC) @refresh(onMutationExecuted: { operation: "AddMovie" }) {
  movies { id title }
}

# Counterpart mutation for ListMoviesSimple
mutation AddMovie($title: String!) @auth(level: USER) {
  movie_insert(data: { title: $title })
}

# Event-driven: Refresh only when a movie of the same genre is added
# Demonstrates the use of 'condition' and 'mutation.variables'
query ListMoviesByGenre($genre: String!) @auth(level: PUBLIC)
  @refresh(onMutationExecuted: {
    operation: "AddMovieWithGenre",
    condition: "mutation.variables.genre == request.variables.genre"
  }) {
  movies(where: { genre: { eq: $genre } }) { id title }
}

# Counterpart mutation for ListMoviesByGenre
mutation AddMovieWithGenre($title: String!, $genre: String!) @auth(level: USER) {
  movie_insert(data: { title: $title, genre: $genre })
}

# Event-driven: Refresh user profile when updated
# Demonstrates condition based on auth context
query MyProfile @auth(level: USER)
  @refresh(onMutationExecuted: {
    operation: "UpdateProfile",
    condition: "mutation.auth.uid == request.auth.uid"
  }) {
  user(uid_expr: "auth.uid") { id name }
}

# Counterpart mutation for MyProfile
mutation UpdateProfile($name: String!) @auth(level: USER) {
  user_update(id_expr: "auth.uid", data: { name: $name })
}

# Time-based: live leaderboard refreshing every 30 seconds
query MovieLeaderboard
  @auth(level: PUBLIC)
  @refresh(every: { seconds: 30 }) {
  movies(orderBy: [{ rating: DESC }], limit: 10) {
    id title rating
  }
}
```

```typescript
import { listMoviesRef, movieLeaderboardRef } from '@movie-app/dataconnect';
import { subscribe } from 'firebase/data-connect';

// Subscribe to movie list — refreshes when AddReview mutation runs
const unsubMovies = subscribe(listMoviesRef({ genre: 'Action' }), {
  onNext: (result) => updateMovieList(result.data.movies),
  onError: (error) => console.error(error)
});

// Subscribe to leaderboard — refreshes every 30 seconds
const unsubLeaderboard = subscribe(movieLeaderboardRef(), {
  onNext: (result) => updateLeaderboard(result.data.movies),
  onError: (error) => console.error(error)
});

// Cleanup
// unsubMovies();
// unsubLeaderboard();
```

---

## E-Commerce Store

Products, orders, and cart management with user authentication.

### Schema

```graphql
# schema.gql

type User @table(key: "uid") {
  uid: String! @default(expr: "auth.uid")
  email: String! @unique
  name: String
  shippingAddress: String
}

type Product @table {
  id: UUID! @default(expr: "uuidV4()")
  name: String! @index
  description: String
  price: Float!
  stock: Int! @default(value: 0)
  category: String @index
  imageUrl: String
}

type CartItem @table(key: ["user", "product"]) {
  user: User!
  product: Product!
  quantity: Int!
}

enum OrderStatus {
  PENDING
  PAID
  SHIPPED
  DELIVERED
  CANCELLED
}

type Order @table {
  id: UUID! @default(expr: "uuidV4()")
  user: User!
  status: OrderStatus! @default(value: PENDING)
  total: Float!
  shippingAddress: String!
  createdAt: Timestamp! @default(expr: "request.time")
}

type OrderItem @table {
  id: UUID! @default(expr: "uuidV4()")
  order: Order!
  product: Product!
  quantity: Int!
  priceAtPurchase: Float!
}
```

### Operations

```graphql
# Public: Browse products
query ListProducts($category: String, $search: String) @auth(level: PUBLIC) {
  products(where: {
    category: { eq: $category },
    name: { contains: $search },
    stock: { gt: 0 }
  }) {
    id name price stock imageUrl
  }
}

# User: View cart
query MyCart @auth(level: USER) {
  cartItems(where: { user: { uid: { eq_expr: "auth.uid" }}}) {
    quantity
    product { id name price imageUrl stock }
  }
}

# User: Add to cart
mutation AddToCart($productId: UUID!, $quantity: Int!) @auth(level: USER) {
  cartItem_upsert(data: {
    user: { uid_expr: "auth.uid" },
    product: { id: $productId },
    quantity: $quantity
  })
}

# User: Checkout (transactional)
mutation Checkout($shippingAddress: String!) 
  @auth(level: USER) 
  @transaction {
  # Query cart items
  query @redact {
    cartItems(where: { user: { uid: { eq_expr: "auth.uid" }}}) 
      @check(expr: "this.size() > 0", message: "Cart is empty") {
      quantity
      product { id price }
    }
  }
  # Create order (in real app, calculate total from cart)
  order_insert(data: {
    user: { uid_expr: "auth.uid" },
    shippingAddress: $shippingAddress,
    total: 0  # Calculate in app logic
  })
}
```

---

## Blog with Permissions

Multi-author blog with role-based permissions.

### Schema

```graphql
# schema.gql

type User @table(key: "uid") {
  uid: String! @default(expr: "auth.uid")
  email: String! @unique
  name: String!
  bio: String
}

enum UserRole {
  VIEWER
  AUTHOR
  EDITOR
  ADMIN
}

type BlogPermission @table(key: ["user"]) {
  user: User!
  role: UserRole! @default(value: VIEWER)
}

enum PostStatus {
  DRAFT
  PUBLISHED
  ARCHIVED
}

type Post @table {
  id: UUID! @default(expr: "uuidV4()")
  author: User!
  title: String! @searchable
  content: String! @searchable
  status: PostStatus! @default(value: DRAFT)
  publishedAt: Timestamp
  createdAt: Timestamp! @default(expr: "request.time")
  updatedAt: Timestamp! @default(expr: "request.time")
}

type Comment @table {
  id: UUID! @default(expr: "uuidV4()")
  post: Post!
  author: User!
  content: String!
  createdAt: Timestamp! @default(expr: "request.time")
}
```

### Operations with Role Checks

```graphql
# Public: Read published posts
query PublishedPosts @auth(level: PUBLIC) {
  posts(
    where: { status: { eq: PUBLISHED }},
    orderBy: [{ publishedAt: DESC }]
  ) {
    id title content publishedAt
    author { name }
  }
}

# Author+: Create post
mutation CreatePost($title: String!, $content: String!) 
  @auth(level: USER) 
  @transaction {
  # Check user is at least AUTHOR
  query @redact {
    blogPermission(key: { user: { uid_expr: "auth.uid" }})
      @check(expr: "this != null", message: "No permission record") {
      role @check(expr: "this in ['AUTHOR', 'EDITOR', 'ADMIN']", message: "Must be author+")
    }
  }
  post_insert(data: {
    author: { uid_expr: "auth.uid" },
    title: $title,
    content: $content
  })
}

# Editor+: Publish any post
mutation PublishPost($id: UUID!) 
  @auth(level: USER) 
  @transaction {
  query @redact {
    blogPermission(key: { user: { uid_expr: "auth.uid" }}) {
      role @check(expr: "this in ['EDITOR', 'ADMIN']", message: "Must be editor+")
    }
  }
  post_update(id: $id, data: {
    status: PUBLISHED,
    publishedAt_expr: "request.time"
  })
}

# Admin: Grant role
mutation GrantRole($userUid: String!, $role: UserRole!) 
  @auth(level: USER) 
  @transaction {
  query @redact {
    blogPermission(key: { user: { uid_expr: "auth.uid" }}) {
      role @check(expr: "this == 'ADMIN'", message: "Must be admin")
    }
  }
  blogPermission_upsert(data: {
    user: { uid: $userUid },
    role: $role
  })
}
```

---

## Native SQL Examples

For scenarios where standard GraphQL cannot express the required database logic, use Native SQL.

### Basic SELECT with field aliasing

```graphql
query GetMoviesByGenre($genre: String!, $limit: Int!) @auth(level: PUBLIC) {
  movies: _select(
    sql: """
      SELECT id, title, release_year, rating
      FROM movie
      WHERE genre = $1
      ORDER BY release_year DESC
      LIMIT $2
    """,
    params: [$genre, $limit]
  )
}
```

### Basic UPDATE

```graphql
mutation UpdateMovieRating($movieId: UUID!, $newRating: Float!) @auth(level: USER) {
  _execute(
    sql: """
      UPDATE movie
      SET rating = $2
      WHERE id = $1
    """,
    params: [$movieId, $newRating]
  )
}
```

### Advanced aggregation with RANK

```graphql
query GetMoviesRankedByRating @auth(level: PUBLIC) {
  _select(
    sql: """
      SELECT
        id,
        title,
        rating,
        RANK() OVER (ORDER BY rating DESC) as rank
      FROM movie
      WHERE rating IS NOT NULL
      LIMIT 20
    """,
    params: []
  )
}
```

### UPDATE with RETURNING and Auth Context

```graphql
mutation UpdateMyReviewText($movieId: UUID!, $newText: String!) @auth(level: USER) {
  updatedReview: _executeReturningFirst(
    sql: """
      UPDATE review
      SET text = $2
      WHERE movie_id = $1 AND user_uid = $3
      RETURNING movie_id, user_uid, rating, text
    """,
    params: [$movieId, $newText, {_expr: "auth.uid"}]
  )
}
```

### Advanced CTE with upserts (atomic get-or-create)

*Note: Data-modifying CTEs are only supported by `_execute`, not `_executeReturning`.*

```graphql
mutation CreateMovieCTE($movieId: UUID!, $userUid: String!, $reviewId: UUID!) @auth(level: USER) {
  _execute(
    sql: """
      WITH
      new_user AS (
        INSERT INTO "user" (uid, email, display_name)
        VALUES ($2, 'auto@example.com', 'Auto-Generated User')
        ON CONFLICT (uid) DO NOTHING
        RETURNING uid
      ),
      movie AS (
        INSERT INTO movie (id, title, poster_url, release_year, genre)
        VALUES ($1, 'Auto-Generated Movie', 'https://placeholder.com', 2025, 'Sci-Fi')
        ON CONFLICT (id) DO NOTHING
        RETURNING id
      )
      INSERT INTO review (id, movie_id, user_uid, rating, text, created_at)
      VALUES (
        $3,
        $1,
        $2,
        5,
        'Good!',
        NOW()
      )
    """,
    params: [$movieId, $userUid, $reviewId]
  )
}
```

### Multi-statement Transactions

Because `mutation` operations are single requests, you can chain multiple `_execute` commands within a `@transaction` to ensure they all succeed or fail together.

```graphql
mutation SafeTransfer($from: UUID!, $to: UUID!, $amount: Float!) @auth(level: USER) @transaction {
  deduct: _execute(
    sql: "UPDATE account SET balance = balance - $2 WHERE id = $1", 
    params: [$from, $amount]
  )
  add: _execute(
    sql: "UPDATE account SET balance = balance + $2 WHERE id = $1", 
    params: [$to, $amount]
  )
}
```

### Use of extensions (e.g. PostGIS for geospatial data)

*Prerequisite:* You must enable the extension on your underlying Cloud SQL instance by connecting to your database as the postgres user and running:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

```graphql
query GetNearbyActiveRestaurants($userLong: Float!, $userLat: Float!, $maxDistanceMeters: Float!) @auth(level: USER) {
  nearby: _select(
    sql: """
      SELECT 
        id, 
        name,
        tags,
        ST_Distance(
          ST_MakePoint((metadata->>'longitude')::float, (metadata->>'latitude')::float)::geography, 
          ST_MakePoint($1, $2)::geography
        ) as distance_meters
      FROM restaurant
      WHERE active = true
        AND metadata ? 'longitude' AND metadata ? 'latitude'
        AND ST_DWithin(
          ST_MakePoint((metadata->>'longitude')::float, (metadata->>'latitude')::float)::geography, 
          ST_MakePoint($1, $2)::geography, 
          $3
        )
      ORDER BY distance_meters ASC
      LIMIT 10
    """,
    params: [$userLong, $userLat, $maxDistanceMeters]
  )
}
```
*After running the query using a client SDK, the result will be in `data.nearby`.*
