# Project Structure

This document provides a detailed overview of the directory structure and file organization of the XiaoShiLiu Image & Text Community project.

## Overall Structure

```
XiaoShiLiu Community/
├── vue3-project/           # Frontend project
├── express-project/        # Backend project
├── README.md              # Main project documentation
├── DEPLOYMENT.md          # Deployment guide
└── PROJECT_STRUCTURE.md   # Project structure documentation (this document)
```

## Frontend Project Structure (vue3-project/)

```
vue3-project/
├── public/                # Static assets directory
│   └── logo.ico          # Website favicon
├── src/                  # Source code directory
│   ├── api/              # API interface encapsulation
│   ├── assets/           # Static assets (images, styles, etc.)
│   ├── components/       # Reusable components
│   ├── composables/      # Composable functions
│   ├── config/           # Configuration files
│   ├── directives/       # Custom directives
│   ├── router/           # Router configuration
│   ├── stores/           # Pinia state management
│   ├── utils/            # Utility functions
│   ├── views/            # Page components
│   ├── App.vue           # Root component
│   └── main.js           # Entry file
├── .env.example          # Environment variables template
├── index.html            # HTML template
├── package.json          # Project configuration
├── vite.config.js        # Vite configuration
└── README.md             # Frontend project documentation
```

### Frontend Directory Details

| Directory/File | Description | Main Content |
|----------------|-------------|-------------|
| `public/` | Static assets directory | Website favicon, static files that don't need compilation |
| `src/api/` | API interface encapsulation | HTTP request wrappers, interface definitions |
| `src/assets/` | Static assets | Images, fonts, style files, etc. |
| `src/components/` | Reusable components | Reusable Vue components |
| `src/composables/` | Composable functions | Vue 3 Composition API logic reuse |
| `src/config/` | Configuration files | Application configuration, constant definitions |
| `src/directives/` | Custom directives | Vue custom directives |
| `src/router/` | Router configuration | Vue Router route definitions |
| `src/stores/` | State management | Pinia state management modules |
| `src/utils/` | Utility functions | Common utility functions, helper methods |
| `src/views/` | Page components | Page-level Vue components |
| `App.vue` | Root component | Application root component |
| `main.js` | Entry file | Application entry point |
| `vite.config.js` | Vite configuration | Build tool configuration |

## Backend Project Structure (express-project/)

```
express-project/
├── config/               # Configuration files directory
│   ├── config.js        # Main configuration file
│   └── database.js      # Database configuration
├── routes/               # Route files directory
│   ├── auth.js          # Authentication routes
│   ├── users.js         # User routes
│   ├── posts.js         # Post routes
│   ├── comments.js      # Comment routes
│   └── ...              # Other route files
├── middleware/           # Middleware directory
│   ├── auth.js          # Authentication middleware
│   └── crudFactory.js   # CRUD factory
├── utils/                # Utility functions directory
├── scripts/              # Script files directory
│   ├── init-database.js # Database initialization script
│   ├── init-database.sql # SQL initialization script
│   ├── generate-data.js # Test data generation script
│   └── update-sample-images.js # Sample image update script
├── app.js               # Application entry file
├── package.json         # Project configuration
└── .env.example         # Environment variables template
```

### Backend Directory Details

| Directory/File | Description | Main Content |
|----------------|-------------|-------------|
| `config/` | Configuration files directory | Application configuration, database configuration |
| `routes/` | Route files directory | Express route definitions, API endpoints |
| `middleware/` | Middleware directory | Express middleware, authentication logic |
| `utils/` | Utility functions directory | Common utility functions, helper methods |
| `scripts/` | Script files directory | Database initialization, data generation scripts |
| `app.js` | Application entry file | Express application entry point |

### Route Files Description

| Route File | Function | Main Endpoints |
|------------|----------|----------------|
| `auth.js` | User authentication | Login, registration, token validation |
| `users.js` | User management | User information CRUD, follow relationships |
| `posts.js` | Post management | Post publishing, editing, deletion, querying |
| `comments.js` | Comment management | Comment publishing, deletion, querying |

### Script Files Description

| Script File | Function | Use Case |
|-------------|----------|----------|
| `init-database.js` | Database initialization | Create database structure on first deployment |
| `init-database.sql` | SQL initialization script | Execute directly in MySQL client |
| `generate-data.js` | Test data generation | Fill test data in development environment |
| `update-sample-images.js` | Image link update | Update sample image resources |

## Technical Architecture

### Frontend Architecture

```
┌─────────────────────────────────────┐
│              Vue 3 App              │
├─────────────────────────────────────┤
│  Views (Pages)  │  Components       │
├─────────────────────────────────────┤
│  Router         │  Stores (State)   │
├─────────────────────────────────────┤
│  API            │  Utils            │
├─────────────────────────────────────┤
│           Vite (Build Tool)         │
└─────────────────────────────────────┘
```

### Backend Architecture

```
┌─────────────────────────────────────┐
│           Express Server            │
├─────────────────────────────────────┤
│  Routes         │ Middleware        │
├─────────────────────────────────────┤
│  Config         │  Utils            │
├─────────────────────────────────────┤
│           MySQL Database            │
└─────────────────────────────────────┘
```

## Data Flow

```
Frontend Vue App
     ↓ HTTP Request
Express Routes
     ↓ Data Processing
Middleware Validation
     ↓ Database Operations
MySQL Database
     ↓ Return Data
Frontend State Update
     ↓ View Rendering
User Interface Display
```