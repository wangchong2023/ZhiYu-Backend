import { Component, type ReactNode } from 'react';
import { Alert, Button } from 'antd';
import type { WithTranslation } from 'react-i18next';
import { withTranslation } from 'react-i18next';

interface Props extends WithTranslation {
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
    const { t } = this.props;
    if (this.state.error) {
      return (
        <Alert
          type="error"
          message={t('common.renderError')}
          description={this.state.error.message}
          action={
            <Button onClick={this.handleReset} type="primary" size="small">
              {t('common.retry')}
            </Button>
          }
          style={{ margin: 24 }}
        />
      );
    }
    return this.props.children;
  }
}

export default withTranslation()(ErrorBoundary);
