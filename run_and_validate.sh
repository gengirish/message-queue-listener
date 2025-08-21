#!/bin/bash
# Shell script to build, test, and validate the Spring Boot Message Queue Listener project

set -e

# Color codes for output formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_header() {
    echo -e "\n${BLUE}================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}================================${NC}\n"
}

check_maven() {
    print_status "Checking Maven installation..."
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed or not in PATH"
        exit 1
    fi
    print_success "Maven found: $(mvn -version | head -n 1)"
}

check_java() {
    print_status "Checking Java installation..."
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi
    print_success "Java found: $(java -version 2>&1 | head -n 1)"
}

check_rabbitmq() {
    print_status "Checking RabbitMQ availability on localhost:5672..."
    if timeout 2 bash -c "</dev/tcp/localhost/5672" 2>/dev/null; then
        print_success "RabbitMQ is running on localhost:5672"
        export RABBITMQ_AVAILABLE=1
    else
        print_warning "RabbitMQ is NOT running on localhost:5672"
        print_warning "RabbitMQ-related tests will be skipped."
        export RABBITMQ_AVAILABLE=0
    fi
}

validate_project_structure() {
    print_status "Validating project structure..."
    required_files=(
        "pom.xml"
        "src/main/java/com/example/messagequeue/MessageQueueListenerApplication.java"
        "src/test/java/com/example/messagequeue/MessageQueueListenerApplicationTest.java"
        "application.yml"
    )
    for file in "${required_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            print_error "Required file missing: $file"
            exit 1
        fi
    done
    print_success "Project structure validation passed"
}

clean_project() {
    print_status "Cleaning project..."
    mvn clean > /dev/null 2>&1 && print_success "Project cleaned successfully"
}

build_project() {
    print_status "Building project..."
    mvn compile -q && print_success "Project compiled successfully"
}

run_tests() {
    print_status "Running tests..."
    if mvn test -q > test_output.log 2>&1; then
        print_success "All tests passed"
        # Show summary from Surefire reports
        for report in target/surefire-reports/TEST-*.xml; do
            if [[ -f "$report" ]]; then
                test_count=$(grep -o 'tests="[0-9]*"' "$report" | grep -o '[0-9]*')
                failures=$(grep -o 'failures="[0-9]*"' "$report" | grep -o '[0-9]*')
                errors=$(grep -o 'errors="[0-9]*"' "$report" | grep -o '[0-9]*')
                test_file=$(basename "$report" .xml | sed 's/TEST-//')
                print_success "Test Results ($test_file): $test_count tests run, $failures failures, $errors errors"
            fi
        done
    else
        print_error "Tests failed"
        cat test_output.log
        exit 1
    fi
}

check_dependencies() {
    print_status "Checking project dependencies..."
    mvn dependency:resolve -q > /dev/null 2>&1 && print_success "All dependencies resolved successfully"
}

generate_report() {
    print_status "Generating validation report..."
    echo "Project Validation Report" > validation_report.txt
    echo "=========================" >> validation_report.txt
    echo "Date: $(date)" >> validation_report.txt
    echo "Project: Spring Boot Message Queue Listener" >> validation_report.txt
    echo "" >> validation_report.txt

    echo "Maven Version:" >> validation_report.txt
    mvn -version >> validation_report.txt 2>&1
    echo "" >> validation_report.txt

    echo "Java Version:" >> validation_report.txt
    java -version >> validation_report.txt 2>&1
    echo "" >> validation_report.txt

    echo "RabbitMQ Availability:" >> validation_report.txt
    if [[ "$RABBITMQ_AVAILABLE" == "1" ]]; then
        echo "RabbitMQ is running on localhost:5672" >> validation_report.txt
    else
        echo "RabbitMQ is NOT running on localhost:5672. RabbitMQ-related tests were skipped." >> validation_report.txt
    fi
    echo "" >> validation_report.txt

    echo "Dependencies:" >> validation_report.txt
    mvn dependency:list -q >> validation_report.txt 2>&1
    echo "" >> validation_report.txt

    echo "Test Results Summary:" >> validation_report.txt
    for report in target/surefire-reports/TEST-*.xml; do
        if [[ -f "$report" ]]; then
            echo "Test Results Summary ($(basename "$report")):" >> validation_report.txt
            grep -E "(tests=|failures=|errors=|time=)" "$report" >> validation_report.txt
        fi
    done

    print_success "Report generated: validation_report.txt"
}

cleanup() {
    print_status "Cleaning up temporary files..."
    rm -f test_output.log
    print_success "Cleanup completed"
}

main() {
    print_header "Spring Boot Message Queue Listener Project Validation"
    check_java
    check_maven
    check_rabbitmq
    validate_project_structure
    clean_project
    check_dependencies
    build_project
    run_tests
    generate_report
    print_success "All validations passed successfully!"
    cleanup
}

trap cleanup EXIT
main "$@"
