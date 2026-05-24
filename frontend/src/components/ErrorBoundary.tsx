import { Component, type ReactNode } from 'react';
import { Alert, Button } from 'antd';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: { componentStack: string }) {
    console.error('ErrorBoundary caught:', error.message, info.componentStack);
  }

  handleReset = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return (
        <Alert
          type="error"
          message="页面渲染异常"
          description={this.state.error.message}
          action={
            <Button onClick={this.handleReset} type="primary" size="small">
              重试
            </Button>
          }
          style={{ margin: 24 }}
        />
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
