CREATE TABLE courses (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         course_code VARCHAR(50) NOT NULL,
                         course_name VARCHAR(255) NOT NULL,
                         instructor_name VARCHAR(255),
                         location VARCHAR(255),
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workplaces (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            company_name VARCHAR(255) NOT NULL,
                            job_title VARCHAR(255),
                            location VARCHAR(255),
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);