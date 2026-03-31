export interface ExecutionEvent {
  type: string
  executionId: string
  nodeId?: string
  nodeName?: string
  nodeType?: string
  status?: string
  duration?: number
  timestamp?: number
  outputs?: Record<string, any>
  errorMessage?: string
  workflowId?: string
  success?: boolean
}

export type ExecutionEventHandler = (event: ExecutionEvent) => void

export class ExecutionWebSocket {
  private ws: WebSocket | null = null
  private executionId: string
  private handlers: Set<ExecutionEventHandler> = new Set()
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 1000
  private baseUrl: string

  constructor(executionId: string) {
    this.executionId = executionId
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    this.baseUrl = `${protocol}//localhost:8080`
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `${this.baseUrl}/ws/execution/${this.executionId}`
      
      try {
        this.ws = new WebSocket(url)
        
        this.ws.onopen = () => {
          console.log(`WebSocket connected for execution: ${this.executionId}`)
          this.reconnectAttempts = 0
          resolve()
        }
        
        this.ws.onmessage = (event) => {
          try {
            const data: ExecutionEvent = JSON.parse(event.data)
            this.handlers.forEach(handler => handler(data))
          } catch (e) {
            console.error('Failed to parse WebSocket message:', e)
          }
        }
        
        this.ws.onerror = (error) => {
          console.error('WebSocket error:', error)
          reject(error)
        }
        
        this.ws.onclose = () => {
          console.log(`WebSocket closed for execution: ${this.executionId}`)
          this.handleReconnect()
        }
      } catch (e) {
        reject(e)
      }
    })
  }

  private handleReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
      
      setTimeout(() => {
        this.connect().catch(e => {
          console.error('Reconnect failed:', e)
        })
      }, this.reconnectDelay * this.reconnectAttempts)
    }
  }

  onEvent(handler: ExecutionEventHandler): () => void {
    this.handlers.add(handler)
    return () => {
      this.handlers.delete(handler)
    }
  }

  send(data: any): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    } else {
      console.warn('WebSocket is not connected')
    }
  }

  close(): void {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.handlers.clear()
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }
}

export function createExecutionWebSocket(executionId: string): ExecutionWebSocket {
  return new ExecutionWebSocket(executionId)
}
